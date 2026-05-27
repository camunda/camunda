/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export const queryKeys = {
  users: {
    all: ["users"] as const,
    search: (params?: unknown) => ["users", "search", params] as const,
    detail: (username: string) => ["users", "detail", username] as const,
  },
  groups: {
    all: ["groups"] as const,
    search: (params?: unknown) => ["groups", "search", params] as const,
    detail: (groupId: string) => ["groups", "detail", groupId] as const,
    members: (groupId: string, params?: unknown) =>
      ["groups", "members", groupId, params] as const,
    roles: (groupId: string, params?: unknown) =>
      ["groups", "roles", groupId, params] as const,
    mappingRules: (groupId: string, params?: unknown) =>
      ["groups", "mappingRules", groupId, params] as const,
    clients: (groupId: string, params?: unknown) =>
      ["groups", "clients", groupId, params] as const,
  },
  roles: {
    all: ["roles"] as const,
    search: (params?: unknown) => ["roles", "search", params] as const,
    detail: (roleId: string) => ["roles", "detail", roleId] as const,
    members: (roleId: string, params?: unknown) =>
      ["roles", "members", roleId, params] as const,
    groups: (roleId: string, params?: unknown) =>
      ["roles", "groups", roleId, params] as const,
    mappingRules: (roleId: string, params?: unknown) =>
      ["roles", "mappingRules", roleId, params] as const,
    clients: (roleId: string, params?: unknown) =>
      ["roles", "clients", roleId, params] as const,
  },
  tenants: {
    all: ["tenants"] as const,
    search: (params?: unknown) => ["tenants", "search", params] as const,
    detail: (tenantId: string) => ["tenants", "detail", tenantId] as const,
    members: (tenantId: string, params?: unknown) =>
      ["tenants", "members", tenantId, params] as const,
    roles: (tenantId: string, params?: unknown) =>
      ["tenants", "roles", tenantId, params] as const,
    groups: (tenantId: string, params?: unknown) =>
      ["tenants", "groups", tenantId, params] as const,
    mappingRules: (tenantId: string, params?: unknown) =>
      ["tenants", "mappingRules", tenantId, params] as const,
    clients: (tenantId: string, params?: unknown) =>
      ["tenants", "clients", tenantId, params] as const,
  },
  authorizations: {
    all: ["authorizations"] as const,
    search: (params?: unknown) => ["authorizations", "search", params] as const,
  },
  mappingRules: {
    all: ["mappingRules"] as const,
    search: (params?: unknown) => ["mappingRules", "search", params] as const,
  },
  auditLogs: {
    all: ["auditLogs"] as const,
    search: (params?: unknown) => ["auditLogs", "search", params] as const,
  },
  clusterVariables: {
    all: ["clusterVariables"] as const,
    search: (params?: unknown) =>
      ["clusterVariables", "search", params] as const,
  },
  globalTaskListeners: {
    all: ["globalTaskListeners"] as const,
    search: (params?: unknown) =>
      ["globalTaskListeners", "search", params] as const,
  },
  messageSubscriptions: {
    all: ["messageSubscriptions"] as const,
    search: (params?: unknown) =>
      ["messageSubscriptions", "search", params] as const,
  },
  license: ["license"] as const,
  authentication: {
    me: ["authentication", "me"] as const,
  },
  setup: ["setup"] as const,
} as const;
