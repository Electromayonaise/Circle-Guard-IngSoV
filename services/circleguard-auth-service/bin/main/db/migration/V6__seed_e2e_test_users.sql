-- Seed users required by E2E test suite
-- BCrypt(password123, rounds=10)
INSERT INTO local_users (username, password_hash, email, is_active) VALUES
('testuser',        '$2b$10$b.vk4jUGRqPZLBzaeCSy.uL0tMhQUFb9dI549Ul5lOE4PMP5HQgLu', 'testuser@test.com',        true),
('notif-testuser',  '$2b$10$b.vk4jUGRqPZLBzaeCSy.uL0tMhQUFb9dI549Ul5lOE4PMP5HQgLu', 'notif@test.com',           true),
('symptom-testuser','$2b$10$b.vk4jUGRqPZLBzaeCSy.uL0tMhQUFb9dI549Ul5lOE4PMP5HQgLu', 'symptom@test.com',         true),
('healthy-testuser','$2b$10$b.vk4jUGRqPZLBzaeCSy.uL0tMhQUFb9dI549Ul5lOE4PMP5HQgLu', 'healthy@test.com',         true)
ON CONFLICT (username) DO NOTHING;

-- BCrypt(admin123, rounds=10)
INSERT INTO local_users (username, password_hash, email, is_active) VALUES
('admin', '$2b$10$GgcFZ0POZcaqZ0R5NGoN6eNMgXGp9rb8hrrw4Y.icAXJAM0UmDn1a', 'admin@test.com', true)
ON CONFLICT (username) DO NOTHING;

-- Assign STUDENT role to regular test users
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM local_users u, roles r
WHERE u.username IN ('testuser', 'notif-testuser', 'symptom-testuser', 'healthy-testuser')
  AND r.name = 'STUDENT'
ON CONFLICT DO NOTHING;

-- Assign HEALTH_CENTER role to admin (required for /admin/status/correct endpoint)
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM local_users u, roles r
WHERE u.username = 'admin' AND r.name = 'HEALTH_CENTER'
ON CONFLICT DO NOTHING;
