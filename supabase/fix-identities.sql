-- ═══════════════════════════════════════════════════════════════════
--  ترمیم ورود کاربران seedشده — این را در SQL Editor اجرا کن
--  (GoTrue برای ورود به رکورد auth.identities نیاز دارد که seed.sql نساخته بود)
-- ═══════════════════════════════════════════════════════════════════

insert into auth.identities (id, user_id, provider_id, provider, identity_data, last_sign_in_at, created_at, updated_at)
select
    gen_random_uuid(),
    u.id,
    u.email,
    'email',
    jsonb_build_object('sub', u.id::text, 'email', u.email, 'email_verified', true),
    now(),
    now(),
    now()
from auth.users u
where not exists (
    select 1 from auth.identities i where i.user_id = u.id and i.provider = 'email'
);
