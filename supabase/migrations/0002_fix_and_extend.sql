-- ═══════════════════════════════════════════════════════════════════
--  اصلاحیه روی schema اولیه — این را بعد از فایل قبلی اجرا کن
-- ═══════════════════════════════════════════════════════════════════

-- device_id در اپ یک رشتهٔ دلخواه است ("dev_" + hex)، نه UUID واقعی
alter table public.device_bindings alter column device_id type text;
alter table public.attendance_records alter column device_id type text;
alter table public.biometric_keys alter column device_id type text;

-- جدول وضعیت میانی «QR تأیید شده، منتظر تأیید صوتی» — معادل Map داخلی MockBackend
create table if not exists public.pending_attendance (
    session_id      uuid not null references public.attendance_sessions (id) on delete cascade,
    student_id      uuid not null references public.users (id) on delete cascade,
    qr_token_id     text not null,
    verified_at     timestamptz not null default now(),
    primary key (session_id, student_id)
);
alter table public.pending_attendance enable row level security;
create policy pending_self on public.pending_attendance
    for all using (student_id = auth.uid());

-- courses باید توسط استاد صاحبش هم قابل select ساده باشد (برای dashboard/session join)
-- (سیاست courses_owner از قبل for all را پوشش می‌دهد، نیازی به تغییر نیست)
