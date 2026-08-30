// ═══════════════════════════════════════════════════════════════════
//  SmartAttendance API — Supabase Edge Function
//  پیاده‌سازی واقعی همان قرارداد app/.../data/remote/api/AttendanceApi.kt
//  (منطق برابر MockBackend.kt است، فقط با Postgres به‌جای Map در حافظه)
// ═══════════════════════════════════════════════════════════════════

import { Hono } from "npm:hono@4";
import { createClient, type SupabaseClient } from "npm:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const QR_HMAC_SECRET = Deno.env.get("QR_HMAC_SECRET")!; // با: openssl rand -hex 32

const admin: SupabaseClient = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);
const authClient: SupabaseClient = createClient(SUPABASE_URL, ANON_KEY);

const QR_TTL_MS = 3_000;
const AUDIO_TTL_MS = 12_000;
const QR_PREFIX = "AT";

class ApiError extends Error {
  constructor(public status: number, public code: string, message?: string) {
    super(message ?? code);
  }
}

// ───────────────────────── HMAC ─────────────────────────

async function hmacKey(): Promise<CryptoKey> {
  return crypto.subtle.importKey(
    "raw",
    new TextEncoder().encode(QR_HMAC_SECRET),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign", "verify"],
  );
}

async function sign(payload: string): Promise<string> {
  const key = await hmacKey();
  const sig = await crypto.subtle.sign("HMAC", key, new TextEncoder().encode(payload));
  return Array.from(new Uint8Array(sig)).map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function verifySig(payload: string, signature: string): Promise<boolean> {
  const expected = await sign(payload);
  if (expected.length !== signature.length) return false;
  let diff = 0;
  for (let i = 0; i < expected.length; i++) diff |= expected.charCodeAt(i) ^ signature.charCodeAt(i);
  return diff === 0;
}

function randomHex(len: number): string {
  const bytes = new Uint8Array(Math.ceil(len / 2));
  crypto.getRandomValues(bytes);
  return Array.from(bytes).map((b) => b.toString(16).toUpperCase().padStart(2, "0")).join("").slice(0, len);
}

// ───────────────────────── Auth helpers ─────────────────────────

type Profile = { id: string; name: string; email: string; role: "PROFESSOR" | "STUDENT"; student_number: string | null };

async function requireUser(req: Request): Promise<Profile> {
  const authHeader = req.headers.get("Authorization");
  if (!authHeader?.startsWith("Bearer ")) throw new ApiError(401, "UNAUTHORIZED");
  const token = authHeader.slice(7);
  const { data, error } = await authClient.auth.getUser(token);
  if (error || !data.user) throw new ApiError(401, "UNAUTHORIZED");
  const { data: profile, error: perr } = await admin
    .from("users").select("*").eq("id", data.user.id).single();
  if (perr || !profile) throw new ApiError(401, "UNAUTHORIZED");
  return profile as Profile;
}

async function requireProfessor(req: Request): Promise<Profile> {
  const u = await requireUser(req);
  if (u.role !== "PROFESSOR") throw new ApiError(401, "UNAUTHORIZED");
  return u;
}

async function requireStudent(req: Request): Promise<Profile> {
  const u = await requireUser(req);
  if (u.role !== "STUDENT") throw new ApiError(401, "UNAUTHORIZED");
  return u;
}

function userDto(p: Profile) {
  return { id: p.id, name: p.name, email: p.email, role: p.role, studentNumber: p.student_number };
}

// ───────────────────────── Time helpers ─────────────────────────

const epoch = (iso: string) => new Date(iso).getTime();
const toIso = (ms: number) => new Date(ms).toISOString();

async function expireStaleSessions() {
  await admin.from("attendance_sessions")
    .update({ status: "EXPIRED" })
    .eq("status", "ACTIVE")
    .lt("expires_at", new Date().toISOString());
}

// ───────────────────────── Domain mappers ─────────────────────────

function sessionDto(row: any, courseName: string) {
  return {
    id: row.id,
    courseId: row.course_id,
    courseName,
    professorId: row.professor_id,
    building: row.building,
    room: row.room,
    startedAt: epoch(row.started_at),
    expiresAt: epoch(row.expires_at),
    windowSeconds: row.window_seconds,
    status: row.status,
  };
}

async function fetchSessionDto(sessionId: string) {
  const { data: row } = await admin.from("attendance_sessions").select("*, courses(name)").eq("id", sessionId).single();
  if (!row) return null;
  return sessionDto(row, (row as any).courses?.name ?? "");
}

async function roster(): Promise<Profile[]> {
  const { data } = await admin.from("users").select("*").eq("role", "STUDENT");
  return (data ?? []) as Profile[];
}

async function presentRecords(sessionId: string) {
  const { data } = await admin.from("attendance_records").select("*").eq("session_id", sessionId);
  return data ?? [];
}

async function rowsFor(sessionId: string, closed: boolean) {
  const [students, present] = await Promise.all([roster(), presentRecords(sessionId)]);
  const rows = students.map((s) => {
    const rec = present.find((r: any) => r.student_id === s.id);
    if (rec) return { studentId: s.id, studentName: s.name, status: "PRESENT", timestamp: epoch(rec.timestamp) };
    return { studentId: s.id, studentName: s.name, status: closed ? "ABSENT" : "PENDING", timestamp: null };
  });
  rows.sort((a, b) => {
    const p = (r: any) => (r.status === "PRESENT" ? 0 : 1);
    if (p(a) !== p(b)) return p(a) - p(b);
    return a.studentName.localeCompare(b.studentName, "fa");
  });
  return rows;
}

async function reportSummaryOf(row: any, courseName: string) {
  const present = await presentRecords(row.id);
  const total = (await roster()).length;
  return {
    sessionId: row.id,
    courseName,
    date: new Date(row.started_at).toLocaleDateString("fa-IR"),
    startedAt: epoch(row.started_at),
    presentCount: present.length,
    absentCount: total - present.length,
  };
}

// ───────────────────────── App ─────────────────────────

const app = new Hono();

app.get("/", (c) => c.text("attendance-api OK"));

app.onError((err, c) => {
  if (err instanceof ApiError) return c.json({ error: err.code }, err.status as any);
  console.error(err);
  return c.json({ error: "SERVER_ERROR" }, 500);
});

// ───────── Auth ─────────

app.post("/auth/login", async (c) => {
  const body = await c.req.json();
  const email = String(body.email ?? "").trim();
  const password = String(body.password ?? "");
  const { data, error } = await authClient.auth.signInWithPassword({ email, password });
  if (error || !data.session) throw new ApiError(401, "INVALID_CREDENTIALS");

  const { data: profile } = await admin.from("users").select("*").eq("id", data.user!.id).single();
  if (!profile) throw new ApiError(401, "INVALID_CREDENTIALS");

  const deviceId = body.deviceId as string | undefined;
  if (profile.role === "STUDENT" && deviceId) {
    await admin.from("device_bindings").upsert({ user_id: profile.id, device_id: deviceId, bound_at: new Date().toISOString() });
  }

  return c.json({ token: data.session.access_token, refreshToken: data.session.refresh_token, user: userDto(profile as Profile) });
});

app.post("/auth/refresh", async (c) => {
  const body = await c.req.json();
  const refreshToken = String(body.refreshToken ?? "");
  if (!refreshToken) throw new ApiError(400, "UNAUTHORIZED");
  const { data, error } = await authClient.auth.refreshSession({ refresh_token: refreshToken });
  if (error || !data.session) throw new ApiError(401, "UNAUTHORIZED");
  return c.json({ token: data.session.access_token, refreshToken: data.session.refresh_token });
});

app.post("/auth/register", async (c) => {
  const body = await c.req.json();
  const name = String(body.name ?? "").trim();
  const email = String(body.email ?? "").trim().toLowerCase();
  const password = String(body.password ?? "");
  const role = String(body.role ?? "STUDENT").toUpperCase() === "PROFESSOR" ? "PROFESSOR" : "STUDENT";
  const studentNumber = body.studentNumber ? String(body.studentNumber).trim() : null;
  if (!name || !email || password.length < 6) throw new ApiError(400, "INVALID_CREDENTIALS");

  const { data: created, error: createErr } = await admin.auth.admin.createUser({
    email,
    password,
    email_confirm: true,
    user_metadata: { full_name: name },
  });
  if (createErr || !created.user) {
    const msg = String((createErr as any)?.message ?? "");
    if (msg.includes("already")) throw new ApiError(409, "EMAIL_TAKEN");
    throw new ApiError(400, "INVALID_CREDENTIALS");
  }

  const { error: perr } = await admin.from("users").insert({
    id: created.user.id,
    name,
    email,
    role,
    student_number: role === "STUDENT" ? studentNumber : null,
  });
  if (perr) {
    await admin.auth.admin.deleteUser(created.user.id);
    throw new ApiError(400, "INVALID_CREDENTIALS");
  }

  return c.json({ ok: true });
});

app.post("/devices/register", async (c) => {
  const user = await requireUser(c.req.raw);
  const body = await c.req.json();
  await admin.from("device_bindings").upsert({ user_id: user.id, device_id: body.deviceId, bound_at: new Date().toISOString() });
  return c.body(null, 200);
});

// ───────── Professor ─────────

app.get("/professor/dashboard", async (c) => {
  const prof = await requireProfessor(c.req.raw);
  await expireStaleSessions();

  const { data: activeRow } = await admin.from("attendance_sessions").select("*, courses(name)")
    .eq("professor_id", prof.id).eq("status", "ACTIVE").maybeSingle();
  const active = activeRow ? sessionDto(activeRow, (activeRow as any).courses?.name ?? "") : null;

  let presentCount = 0;
  let pendingCount = 0;
  if (active) {
    const present = await presentRecords(active.id);
    presentCount = present.length;
    pendingCount = (await roster()).length - presentCount;
  }

  const { data: courseRows } = await admin.from("courses").select("*");
  const todayCourses = (courseRows ?? []).map((cr: any) => ({
    courseName: cr.name,
    room: `${cr.building} - ${cr.room}`,
    time: "",
  }));

  const { data: closedRows } = await admin.from("attendance_sessions").select("*, courses(name)")
    .eq("professor_id", prof.id).eq("status", "CLOSED").order("started_at", { ascending: false }).limit(4);
  const recentSessions = [];
  for (const row of closedRows ?? []) {
    recentSessions.push(await reportSummaryOf(row, (row as any).courses?.name ?? ""));
  }

  return c.json({
    professorName: prof.name,
    activeSession: active,
    presentCount,
    absentCount: 0,
    pendingCount,
    todayCourses,
    recentSessions,
  });
});

app.get("/professor/courses", async (c) => {
  const prof = await requireProfessor(c.req.raw);
  const { data } = await admin.from("courses").select("*").eq("professor_id", prof.id);
  return c.json((data ?? []).map((r: any) => ({ id: r.id, name: r.name, building: r.building, room: r.room })));
});

app.post("/professor/courses", async (c) => {
  const prof = await requireProfessor(c.req.raw);
  const body = await c.req.json();
  const name = String(body.name ?? "").trim();
  const building = String(body.building ?? "").trim() || "-";
  const room = String(body.room ?? "").trim() || "-";
  if (!name) throw new ApiError(400, "UNKNOWN");
  const { data: row, error } = await admin.from("courses").insert({
    professor_id: prof.id, name, building, room,
  }).select("*").single();
  if (error || !row) throw new ApiError(500, "SERVER_ERROR");
  return c.json({ id: row.id, name: row.name, building: row.building, room: row.room });
});

app.post("/professor/sessions", async (c) => {
  const prof = await requireProfessor(c.req.raw);
  const body = await c.req.json();
  const { data: course } = await admin.from("courses").select("*").eq("id", body.courseId).single();
  if (!course) throw new ApiError(404, "UNKNOWN");

  const startedAt = Date.now();
  const windowMinutes = Number(body.windowMinutes ?? 5);
  const { data: row, error } = await admin.from("attendance_sessions").insert({
    course_id: course.id,
    professor_id: prof.id,
    building: body.building || course.building,
    room: body.room || course.room,
    started_at: toIso(startedAt),
    expires_at: toIso(startedAt + windowMinutes * 60_000),
    window_seconds: windowMinutes * 60,
    status: "ACTIVE",
  }).select("*").single();
  if (error || !row) throw new ApiError(500, "SERVER_ERROR");

  return c.json(sessionDto(row, course.name));
});

app.get("/professor/sessions/active", async (c) => {
  const prof = await requireProfessor(c.req.raw);
  await expireStaleSessions();
  const { data: row } = await admin.from("attendance_sessions").select("*, courses(name)")
    .eq("professor_id", prof.id).eq("status", "ACTIVE").maybeSingle();
  if (!row) return c.body(null, 204);
  return c.json(sessionDto(row, (row as any).courses?.name ?? ""));
});

async function ownedActiveSession(professorId: string, sessionId: string) {
  await expireStaleSessions();
  const { data: row } = await admin.from("attendance_sessions").select("*").eq("id", sessionId).single();
  if (!row) throw new ApiError(404, "UNKNOWN");
  if (row.professor_id !== professorId) throw new ApiError(401, "UNAUTHORIZED");
  return row;
}

app.get("/professor/sessions/:id/qr-token", async (c) => {
  const prof = await requireProfessor(c.req.raw);
  const sessionId = c.req.param("id");
  const session = await ownedActiveSession(prof.id, sessionId);
  if (session.status !== "ACTIVE") throw new ApiError(409, session.status === "CLOSED" ? "SESSION_CLOSED" : "SESSION_EXPIRED");

  const { data: current } = await admin.from("qr_tokens").select("*").eq("session_id", sessionId)
    .order("issued_at", { ascending: false }).limit(1).maybeSingle();

  if (current && Date.now() < epoch(current.expires_at)) {
    const payload = `${sessionId}|${current.token_id}|${epoch(current.issued_at)}|${epoch(current.expires_at)}|${current.nonce}`;
    return c.json({ fullToken: `${QR_PREFIX}|${payload}|${current.signature}`, expiresAt: epoch(current.expires_at) });
  }

  const issued = Date.now();
  const expires = issued + QR_TTL_MS;
  const tokenId = randomHex(8);
  const nonce = randomHex(8);
  const payload = `${sessionId}|${tokenId}|${issued}|${expires}|${nonce}`;
  const signature = await sign(payload);
  await admin.from("qr_tokens").insert({
    session_id: sessionId, token_id: tokenId, nonce, signature,
    issued_at: toIso(issued), expires_at: toIso(expires),
  });
  return c.json({ fullToken: `${QR_PREFIX}|${payload}|${signature}`, expiresAt: expires });
});

app.get("/professor/sessions/:id/audio-challenge", async (c) => {
  const prof = await requireProfessor(c.req.raw);
  const sessionId = c.req.param("id");
  const session = await ownedActiveSession(prof.id, sessionId);
  if (session.status !== "ACTIVE") throw new ApiError(409, session.status === "CLOSED" ? "SESSION_CLOSED" : "SESSION_EXPIRED");

  const { data: current } = await admin.from("audio_challenges").select("*").eq("session_id", sessionId)
    .gt("expires_at", new Date().toISOString()).order("issued_at", { ascending: false }).limit(1).maybeSingle();
  if (current) {
    return c.json({ challengeId: current.challenge_id, token: current.token, expiresAt: epoch(current.expires_at) });
  }

  const created = Date.now();
  const record = { challenge_id: randomHex(4), token: randomHex(8), issued_at: toIso(created), expires_at: toIso(created + AUDIO_TTL_MS) };
  await admin.from("audio_challenges").insert({ session_id: sessionId, ...record });
  return c.json({ challengeId: record.challenge_id, token: record.token, expiresAt: created + AUDIO_TTL_MS });
});

app.get("/professor/sessions/:id/monitor", async (c) => {
  const prof = await requireProfessor(c.req.raw);
  const sessionId = c.req.param("id");
  const row = await ownedActiveSession(prof.id, sessionId);
  const session = await fetchSessionDto(sessionId);
  const present = await presentRecords(sessionId);
  const rows = await rowsFor(sessionId, row.status === "CLOSED");
  const total = (await roster()).length;
  return c.json({
    session,
    presentCount: present.length,
    pendingCount: total - present.length,
    absentCount: row.status === "CLOSED" ? total - present.length : 0,
    rows,
  });
});

app.post("/professor/sessions/:id/close", async (c) => {
  const prof = await requireProfessor(c.req.raw);
  const sessionId = c.req.param("id");
  await ownedActiveSession(prof.id, sessionId);
  await admin.from("attendance_sessions").update({ status: "CLOSED" }).eq("id", sessionId);
  await admin.from("qr_tokens").delete().eq("session_id", sessionId);
  await admin.from("audio_challenges").delete().eq("session_id", sessionId);
  await admin.from("pending_attendance").delete().eq("session_id", sessionId);
  return c.body(null, 200);
});

app.get("/professor/reports", async (c) => {
  const prof = await requireProfessor(c.req.raw);
  const { data: rows } = await admin.from("attendance_sessions").select("*, courses(name)")
    .eq("professor_id", prof.id).eq("status", "CLOSED").order("started_at", { ascending: false });
  const out = [];
  for (const row of rows ?? []) out.push(await reportSummaryOf(row, (row as any).courses?.name ?? ""));
  return c.json(out);
});

app.get("/professor/reports/:id", async (c) => {
  const prof = await requireProfessor(c.req.raw);
  const sessionId = c.req.param("id");
  const { data: row } = await admin.from("attendance_sessions").select("*, courses(name)").eq("id", sessionId).single();
  if (!row) throw new ApiError(404, "UNKNOWN");
  if (row.professor_id !== prof.id) throw new ApiError(401, "UNAUTHORIZED");
  const summary = await reportSummaryOf(row, (row as any).courses?.name ?? "");
  const rows = await rowsFor(sessionId, true);
  return c.json({ summary, rows });
});

// ───────── Student ─────────

app.get("/student/home", async (c) => {
  const student = await requireStudent(c.req.raw);
  await expireStaleSessions();
  const { data: activeRow } = await admin.from("attendance_sessions").select("*, courses(name)")
    .eq("status", "ACTIVE").order("started_at", { ascending: false }).limit(1).maybeSingle();
  const active = activeRow ? sessionDto(activeRow, (activeRow as any).courses?.name ?? "") : null;
  const recent = await historyItems(student.id);
  return c.json({ studentName: student.name, studentNumber: student.student_number ?? "", activeSession: active, recent: recent.slice(0, 4) });
});

app.post("/attendance/verify-qr", async (c) => {
  const student = await requireStudent(c.req.raw);
  await expireStaleSessions();
  const body = await c.req.json();
  const qrPayload = String(body.qrPayload ?? "").trim();
  const parts = qrPayload.split("|");
  if (parts.length !== 7 || parts[0] !== QR_PREFIX) throw new ApiError(400, "QR_INVALID");
  const [, sessionId, tokenId, issuedAt, expiresAt, nonce, signature] = parts;
  const signedPayload = `${sessionId}|${tokenId}|${issuedAt}|${expiresAt}|${nonce}`;
  if (!(await verifySig(signedPayload, signature))) throw new ApiError(400, "QR_INVALID");

  const { data: session } = await admin.from("attendance_sessions").select("*, courses(name)").eq("id", sessionId).single();
  if (!session) throw new ApiError(400, "QR_INVALID");
  if (session.status === "CLOSED") throw new ApiError(409, "SESSION_CLOSED");
  if (session.status === "EXPIRED") throw new ApiError(409, "SESSION_EXPIRED");
  if (session.status === "CREATED") throw new ApiError(400, "QR_INVALID");
  if (Date.now() > Number(expiresAt)) throw new ApiError(410, "QR_EXPIRED");

  await admin.from("pending_attendance").upsert({
    session_id: sessionId, student_id: student.id, qr_token_id: tokenId, verified_at: new Date().toISOString(),
  });

  return c.json({ sessionId, courseName: (session as any).courses?.name ?? "" });
});

app.post("/attendance/verify-audio", async (c) => {
  const student = await requireStudent(c.req.raw);
  const body = await c.req.json();
  const sessionId = String(body.sessionId ?? "");

  const { data: existing } = await admin.from("attendance_records").select("session_id")
    .eq("session_id", sessionId).eq("student_id", student.id).maybeSingle();
  if (existing) throw new ApiError(409, "ALREADY_ATTENDED");

  const { data: session } = await admin.from("attendance_sessions").select("*").eq("id", sessionId).single();
  if (!session) throw new ApiError(400, "QR_INVALID");
  if (session.status === "CLOSED") throw new ApiError(409, "SESSION_CLOSED");
  if (session.status === "EXPIRED" || Date.now() > epoch(session.expires_at)) throw new ApiError(409, "SESSION_EXPIRED");

  const { data: pending } = await admin.from("pending_attendance").select("*")
    .eq("session_id", sessionId).eq("student_id", student.id).maybeSingle();
  if (!pending) throw new ApiError(400, "QR_REQUIRED");

  if (!body.biometricAttested) throw new ApiError(400, "BIOMETRIC_FAILED");

  const { data: binding } = await admin.from("device_bindings").select("*").eq("user_id", student.id).maybeSingle();
  if (binding && binding.device_id !== body.deviceId) throw new ApiError(409, "DEVICE_MISMATCH");

  const { data: challenge } = await admin.from("audio_challenges").select("*")
    .eq("session_id", sessionId).ilike("token", String(body.audioToken ?? "").trim()).maybeSingle();
  if (!challenge) throw new ApiError(400, "AUDIO_INVALID");
  if (Date.now() > epoch(challenge.expires_at)) throw new ApiError(410, "CHALLENGE_EXPIRED");

  const timestamp = new Date().toISOString();
  const { error: insertErr } = await admin.from("attendance_records").insert({
    session_id: sessionId,
    student_id: student.id,
    timestamp,
    status: "PRESENT",
    qr_verified: true,
    biometric_verified: !!body.biometricAttested,
    audio_verified: true,
    device_id: body.deviceId || "unknown",
  });
  if (insertErr) throw new ApiError(409, "ALREADY_ATTENDED");

  await admin.from("pending_attendance").delete().eq("session_id", sessionId).eq("student_id", student.id);

  return c.json({
    record: {
      sessionId, studentId: student.id, studentName: student.name,
      timestamp: epoch(timestamp), status: "PRESENT",
      qrVerified: true, biometricVerified: !!body.biometricAttested, audioVerified: true,
      deviceId: body.deviceId || "unknown",
    },
  });
});

app.get("/student/history", async (c) => {
  const student = await requireStudent(c.req.raw);
  await expireStaleSessions();
  return c.json(await historyItems(student.id));
});

async function historyItems(studentId: string) {
  const { data: sessions } = await admin.from("attendance_sessions").select("*, courses(name)").order("started_at", { ascending: false });
  const items = [];
  for (const s of sessions ?? []) {
    const { data: rec } = await admin.from("attendance_records").select("*").eq("session_id", s.id).eq("student_id", studentId).maybeSingle();
    if (rec) {
      items.push({ courseName: (s as any).courses?.name ?? "", date: new Date(s.started_at).toLocaleDateString("fa-IR"), status: "PRESENT", timestamp: epoch(rec.timestamp) });
    } else if (s.status === "CLOSED") {
      items.push({ courseName: (s as any).courses?.name ?? "", date: new Date(s.started_at).toLocaleDateString("fa-IR"), status: "ABSENT", timestamp: null });
    }
  }
  return items;
}

// ───────── Security ─────────

app.post("/security/integrity", async (c) => {
  await requireUser(c.req.raw);
  // TODO تولید واقعی: توکن Play Integrity را با Google verify کن.
  return c.json({ verdict: "PASSES" });
});

app.get("/debug-auth", async (c) => {
  if (c.req.query("key") !== QR_HMAC_SECRET) return c.json({ error: "FORBIDDEN" }, 403);
  const { data, error } = await admin.auth.admin.listUsers({ page: 1, perPage: 50 });
  if (error) return c.json({ step: "listUsers", listError: String((error as any).message ?? error) }, 500);
  const prof = data.users.find((u) => (u.email ?? "").toLowerCase() === "prof@uni.edu");
  const summary = data.users.map((u) => ({
    email: u.email,
    confirmed: !!u.email_confirmed_at,
    identities: (u.identities ?? []).map((i) => ({ provider: i.provider, providerId: i.provider_id })),
    lastSignIn: u.last_sign_in_at,
    created: u.created_at,
  }));
  if (!prof) return c.json({ total: data.users.length, users: summary, prof: "NOT_FOUND" });
  const { error: upErr } = await admin.auth.admin.updateUserById(prof.id, { user_metadata: { debug_probe: Date.now() } });
  return c.json({
    total: data.users.length,
    users: summary,
    profId: prof.id,
    updateTest: upErr ? `UPDATE_FAILED: ${String((upErr as any).message ?? upErr)}` : "UPDATE_OK",
  });
});

app.post("/repair-seed", async (c) => {
  if (c.req.query("key") !== QR_HMAC_SECRET) return c.json({ error: "FORBIDDEN" }, 403);
  const people = [
    { email: "prof@uni.edu", name: "دکتر علی محمدی", role: "PROFESSOR", studentNumber: null },
    { email: "ali@uni.edu", name: "علی رضایی", role: "STUDENT", studentNumber: "40112233" },
    { email: "sara@uni.edu", name: "سارا احمدی", role: "STUDENT", studentNumber: "40112234" },
    { email: "reza@uni.edu", name: "رضا کریمی", role: "STUDENT", studentNumber: "40112235" },
    { email: "maryam@uni.edu", name: "مریم حسینی", role: "STUDENT", studentNumber: "40112236" },
    { email: "hossein@uni.edu", name: "حسین نوری", role: "STUDENT", studentNumber: "40112237" },
  ];
  const out = [];
  const ids: Record<string, string> = {};
  for (const p of people) {
    const { data, error } = await admin.auth.admin.createUser({
      email: p.email,
      password: "12345678",
      email_confirm: true,
      user_metadata: { full_name: p.name },
    });
    if (error) { out.push({ email: p.email, error: error.message }); continue; }
    ids[p.email] = data.user!.id;
    const { error: perr } = await admin.from("users").insert({
      id: data.user!.id, name: p.name, email: p.email, role: p.role, student_number: p.studentNumber,
    });
    out.push({ email: p.email, id: data.user!.id, profile: perr ? `PROFILE_FAILED: ${perr.message}` : "OK" });
  }
  const profId = ids["prof@uni.edu"];
  let courses: string | null = null;
  if (profId) {
    const { error: cerr } = await admin.from("courses").insert([
      { professor_id: profId, name: "ساختمان داده", building: "مهندسی", room: "204" },
      { professor_id: profId, name: "شبکه‌های کامپیوتری", building: "مهندسی", room: "310" },
      { professor_id: profId, name: "سیستم عامل", building: "مهندسی", room: "112" },
    ]);
    courses = cerr ? `COURSES_FAILED: ${cerr.message}` : "OK";
  }
  return c.json({ out, courses });
});

app.all("*", (c) => c.json({ seenPath: new URL(c.req.raw.url).pathname, method: c.req.method }, 404));

Deno.serve((req) => {
  const url = new URL(req.url);
  const stripped = url.pathname
    .replace(/^\/functions\/v1\/attendance-api/, "")
    .replace(/^\/attendance-api/, "");
  const path = stripped === "" ? "/" : stripped;
  return app.fetch(new Request(new URL(path + url.search, url.origin), req));
});
