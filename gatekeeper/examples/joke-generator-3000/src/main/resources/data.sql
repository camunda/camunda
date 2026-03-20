-- Seed data — only inserts if tables are empty (idempotent)

INSERT INTO jokes (setup, punchline, category, created_by, created_at)
SELECT 'Why do programmers prefer dark mode?', 'Because light attracts bugs.', 'programming', 'system', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM jokes WHERE setup = 'Why do programmers prefer dark mode?');

INSERT INTO jokes (setup, punchline, category, created_by, created_at)
SELECT 'Why did the developer go broke?', 'Because he used up all his cache.', 'programming', 'system', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM jokes WHERE setup = 'Why did the developer go broke?');

INSERT INTO jokes (setup, punchline, category, created_by, created_at)
SELECT 'What do you call a fake noodle?', 'An impasta.', 'general', 'system', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM jokes WHERE setup = 'What do you call a fake noodle?');

INSERT INTO jokes (setup, punchline, category, created_by, created_at)
SELECT 'Why don''t scientists trust atoms?', 'Because they make up everything.', 'science', 'system', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM jokes WHERE setup LIKE 'Why don''t scientists%');

INSERT INTO jokes (setup, punchline, category, created_by, created_at)
SELECT 'What did the ocean say to the beach?', 'Nothing, it just waved.', 'general', 'system', CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM jokes WHERE setup = 'What did the ocean say to the beach?');

-- Seed users for basic auth (password is bcrypt of "password")
INSERT INTO app_users (username, password, display_name, email)
SELECT 'user', '{bcrypt}$2a$10$j/7MzRHxhwmlggS92imbo.0W1dpdBtafUvQoQPr8s0N8WTcJvdTk.', 'Regular User', 'user@example.com'
WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE username = 'user');

INSERT INTO app_users (username, password, display_name, email)
SELECT 'admin', '{bcrypt}$2a$10$j/7MzRHxhwmlggS92imbo.0W1dpdBtafUvQoQPr8s0N8WTcJvdTk.', 'Admin User', 'admin@example.com'
WHERE NOT EXISTS (SELECT 1 FROM app_users WHERE username = 'admin');

INSERT INTO user_roles (user_id, role_name)
SELECT (SELECT id FROM app_users WHERE username = 'admin'), 'joke-admin'
WHERE NOT EXISTS (SELECT 1 FROM user_roles WHERE role_name = 'joke-admin');
