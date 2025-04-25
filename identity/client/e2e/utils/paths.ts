/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Any changes made here should be reflected in the corresponding file for the E2E tests in e2e>utils>paths.ts
export const Paths = {
  login() {
    return "/login";
  },
  forbidden() {
    return "/forbidden";
  },
  mappings() {
    return "/mappings";
  },
  users() {
    return "/users";
  },
  groups() {
    return "/groups";
  },
  roles() {
    return "/roles";
  },
  tenants() {
    return "/tenants";
  },
  authorizations() {
    return "/authorizations";
  },
} as const;
