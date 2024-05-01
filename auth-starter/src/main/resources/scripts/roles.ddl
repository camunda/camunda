/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
create table role_authorities
(
    role_name varchar(50) not null,
    authority varchar(50) not null
);

insert into role_authorities values ('ROLE_STAFF', 'write:*');
insert into role_authorities values ('ROLE_USER', 'read:*');
insert into role_authorities values ('ROLE_GUEST', 'read:*');
insert into role_authorities values ('ROLE_ADMIN', 'write:*');
