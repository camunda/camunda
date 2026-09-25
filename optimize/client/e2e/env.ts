/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export const env = {
  optimizeUrl: process.env.E2E_OPTIMIZE_URL ?? 'http://localhost:3000',
  camundaUrl: process.env.E2E_CAMUNDA_URL ?? 'http://localhost:8080',
  databaseUrl: process.env.E2E_DATABASE_URL ?? 'http://localhost:9200',
  camundaUser: process.env.E2E_CAMUNDA_USER ?? 'demo',
  camundaPassword: process.env.E2E_CAMUNDA_PASSWORD ?? 'demo',
};
