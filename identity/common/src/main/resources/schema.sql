/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

-- Ref: https://docs.spring.io/spring-security/reference/servlet/appendix/database-schema.html#_user_schema
CREATE TABLE IF NOT EXISTS users (
    username varchar not null primary key,
    password varchar not null,
    enabled boolean not null
);

CREATE TABLE IF NOT EXISTS authorities (
    username varchar not null,
    authority varchar not null,
    constraint fk_authorities_users foreign key(username) references users(username)
);
CREATE UNIQUE INDEX IF NOT EXISTS ix_auth_username on authorities (username,authority);

CREATE TABLE IF NOT EXISTS role_authorities
(
    role_name varchar not null,
    authority varchar not null
);
