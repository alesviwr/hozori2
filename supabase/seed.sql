-- ═══════════════════════════════════════════════════════════════════
--  Seed — همان کاربران/درس‌های نمونهٔ MockBackend، این‌بار واقعی
--  رمز همه: 12345678   (بعداً حتماً عوض کن)
-- ═══════════════════════════════════════════════════════════════════

-- ───────── auth.users (با پسورد هش‌شده به روش Supabase) ─────────
-- این الگو همان چیزی است که خود Supabase برای seed کردن کاربر تست مستند کرده.

do $$
declare
    v_id uuid;
begin
    -- استاد
    insert into auth.users (
        instance_id, id, aud, role, email, encrypted_password,
        email_confirmed_at, created_at, updated_at,
        raw_app_meta_data, raw_user_meta_data, confirmation_token, recovery_token
    ) values (
        '00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated',
        'prof@uni.edu', crypt('12345678', gen_salt('bf')),
        now(), now(), now(), '{"provider":"email","providers":["email"]}', '{}', '', ''
    ) returning id into v_id;
    insert into public.users (id, name, email, role, student_number)
        values (v_id, 'دکتر علی محمدی', 'prof@uni.edu', 'PROFESSOR', null);

    insert into auth.users (
        instance_id, id, aud, role, email, encrypted_password,
        email_confirmed_at, created_at, updated_at,
        raw_app_meta_data, raw_user_meta_data, confirmation_token, recovery_token
    ) values (
        '00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated',
        'ali@uni.edu', crypt('12345678', gen_salt('bf')),
        now(), now(), now(), '{"provider":"email","providers":["email"]}', '{}', '', ''
    ) returning id into v_id;
    insert into public.users (id, name, email, role, student_number)
        values (v_id, 'علی رضایی', 'ali@uni.edu', 'STUDENT', '40112233');

    insert into auth.users (
        instance_id, id, aud, role, email, encrypted_password,
        email_confirmed_at, created_at, updated_at,
        raw_app_meta_data, raw_user_meta_data, confirmation_token, recovery_token
    ) values (
        '00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated',
        'sara@uni.edu', crypt('12345678', gen_salt('bf')),
        now(), now(), now(), '{"provider":"email","providers":["email"]}', '{}', '', ''
    ) returning id into v_id;
    insert into public.users (id, name, email, role, student_number)
        values (v_id, 'سارا احمدی', 'sara@uni.edu', 'STUDENT', '40112234');

    insert into auth.users (
        instance_id, id, aud, role, email, encrypted_password,
        email_confirmed_at, created_at, updated_at,
        raw_app_meta_data, raw_user_meta_data, confirmation_token, recovery_token
    ) values (
        '00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated',
        'reza@uni.edu', crypt('12345678', gen_salt('bf')),
        now(), now(), now(), '{"provider":"email","providers":["email"]}', '{}', '', ''
    ) returning id into v_id;
    insert into public.users (id, name, email, role, student_number)
        values (v_id, 'رضا کریمی', 'reza@uni.edu', 'STUDENT', '40112235');

    insert into auth.users (
        instance_id, id, aud, role, email, encrypted_password,
        email_confirmed_at, created_at, updated_at,
        raw_app_meta_data, raw_user_meta_data, confirmation_token, recovery_token
    ) values (
        '00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated',
        'maryam@uni.edu', crypt('12345678', gen_salt('bf')),
        now(), now(), now(), '{"provider":"email","providers":["email"]}', '{}', '', ''
    ) returning id into v_id;
    insert into public.users (id, name, email, role, student_number)
        values (v_id, 'مریم حسینی', 'maryam@uni.edu', 'STUDENT', '40112236');

    insert into auth.users (
        instance_id, id, aud, role, email, encrypted_password,
        email_confirmed_at, created_at, updated_at,
        raw_app_meta_data, raw_user_meta_data, confirmation_token, recovery_token
    ) values (
        '00000000-0000-0000-0000-000000000000', gen_random_uuid(), 'authenticated', 'authenticated',
        'hossein@uni.edu', crypt('12345678', gen_salt('bf')),
        now(), now(), now(), '{"provider":"email","providers":["email"]}', '{}', '', ''
    ) returning id into v_id;
    insert into public.users (id, name, email, role, student_number)
        values (v_id, 'حسین نوری', 'hossein@uni.edu', 'STUDENT', '40112237');
end $$;

-- ───────── درس‌ها (متصل به استاد بالا) ─────────
insert into public.courses (professor_id, name, building, room)
select id, 'ساختمان داده', 'مهندسی', '204' from public.users where email = 'prof@uni.edu'
union all
select id, 'شبکه‌های کامپیوتری', 'مهندسی', '310' from public.users where email = 'prof@uni.edu'
union all
select id, 'سیستم عامل', 'مهندسی', '112' from public.users where email = 'prof@uni.edu';
