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

const baseUrlByApp: Record<string, string> = {
  operate: process.env.CORE_APPLICATION_OPERATE_URL ?? 'http://localhost:8081',
  tasklist:
    process.env.CORE_APPLICATION_TASKLIST_URL ?? 'http://localhost:8080',
  identity:
    process.env.CORE_APPLICATION_IDENTITY_URL ?? 'http://localhost:8080',
};

export const relativizePath = (path: string): string => {
  const app = path.split('/')[1];
  const base = baseUrlByApp[app] ?? '';
  return `${base}${path}`;
};
