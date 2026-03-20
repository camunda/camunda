CREATE TABLE app_users (
    id          BIGSERIAL    PRIMARY KEY,
    username    VARCHAR(100) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    display_name VARCHAR(200) NOT NULL,
    email       VARCHAR(200)
);

CREATE TABLE user_roles (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES app_users(id),
    role_name   VARCHAR(100) NOT NULL,
    UNIQUE(user_id, role_name)
);
