/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export const Paths = {
  login(application: string): string {
    // TODO(#46027): This can be removed after sufficient migration period.
    if (application === 'identity') {
      return '/admin/login';
    }
    return `/${application}/login`;
  },
  forbidden(application: string): string {
    // TODO(#46027): This can be removed after sufficient migration period.
    if (application === 'identity') {
      return '/admin/forbidden';
    }
    return `/${application}/forbidden`;
  },
  mappingRules() {
    return '/admin/mapping-rules';
  },
  users() {
    return '/admin/users';
  },
  groups() {
    return '/admin/groups';
  },
  roles() {
    return '/admin/roles';
  },
  tenants() {
    return '/admin/tenants';
  },
  authorizations() {
    return '/admin/authorizations';
  },
  operateDashboard() {
    return '/operate';
  },
  operateProcesses(queryParams?: string) {
    return queryParams
      ? `/operate/processes?${queryParams}`
      : '/operate/processes';
  },
  operateDecisions(queryParams?: string) {
    return queryParams
      ? `/operate/decisions?${queryParams}`
      : '/operate/decisions';
  },
} as const;

export const relativizePath = (path: string) => {
  return `.${path}`;
};
