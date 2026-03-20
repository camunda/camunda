-- Seed jokes
INSERT INTO jokes (setup, punchline, category, created_by) VALUES
    ('Why do programmers prefer dark mode?', 'Because light attracts bugs.', 'programming', 'system'),
    ('Why did the developer go broke?', 'Because he used up all his cache.', 'programming', 'system'),
    ('What do you call a fake noodle?', 'An impasta.', 'general', 'system'),
    ('Why don''t scientists trust atoms?', 'Because they make up everything.', 'science', 'system'),
    ('What did the ocean say to the beach?', 'Nothing, it just waved.', 'general', 'system');

-- Seed users for basic auth (passwords are bcrypt of "password")
INSERT INTO app_users (username, password, display_name, email) VALUES
    ('user', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Regular User', 'user@example.com'),
    ('admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Admin User', 'admin@example.com');

INSERT INTO user_roles (user_id, role_name) VALUES
    ((SELECT id FROM app_users WHERE username = 'admin'), 'joke-admin');
