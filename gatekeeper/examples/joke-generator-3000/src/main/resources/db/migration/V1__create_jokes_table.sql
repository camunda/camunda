CREATE TABLE jokes (
    id          BIGSERIAL PRIMARY KEY,
    setup       TEXT      NOT NULL,
    punchline   TEXT      NOT NULL,
    category    VARCHAR(50) NOT NULL DEFAULT 'general',
    created_by  VARCHAR(100) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
