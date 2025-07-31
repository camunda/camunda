/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export const Paths = {
  login(application: string): string {
    return `/${application}/login`;
  },
  forbidden(application: string): string {
    return `/${application}/forbidden`;
  },
  mappingRules() {
    return '/identity/mapping-rules';
  },
  users() {
    return '/identity/users';
  },
  groups() {
    return '/identity/groups';
  },
  roles() {
    return '/identity/roles';
  },
  tenants() {
    return '/identity/tenants';
  },
  authorizations() {
    return '/identity/authorizations';
  },
} as const;

export const relativizePath = (path: string) => {
  return `.${path}`;
};
