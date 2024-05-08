/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const endpoint = `http://localhost:${process.env.PORT}`;

const config = {
  endpoint,
  agentUser: {
    username: 'demo',
    password: 'demo',
  },
  e2eBasePath: process.env.E2E_BASE_PATH ?? './e2e-playwright',
} as const;

export {config};
