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
    search: (params?: unknown) =>
      !params
        ? (["users", "search"] as const)
        : (["users", "search", params] as const),
    detail: (username: string) => ["users", "detail", username] as const,
  },
  groups: {
    all: ["groups"] as const,
    search: (params?: unknown) =>
      !params
        ? (["groups", "search"] as const)
        : (["groups", "search", params] as const),
    detail: (groupId: string) => ["groups", "detail", groupId] as const,
    members: (groupId: string, params?: unknown) =>
      !params
        ? (["groups", "members", groupId] as const)
        : (["groups", "members", groupId, params] as const),
    roles: (groupId: string, params?: unknown) =>
      !params
        ? (["groups", "roles", groupId] as const)
        : (["groups", "roles", groupId, params] as const),
    mappingRules: (groupId: string, params?: unknown) =>
      !params
        ? (["groups", "mappingRules", groupId] as const)
        : (["groups", "mappingRules", groupId, params] as const),
    clients: (groupId: string, params?: unknown) =>
      !params
        ? (["groups", "clients", groupId] as const)
        : (["groups", "clients", groupId, params] as const),
  },
  roles: {
    all: ["roles"] as const,
    search: (params?: unknown) =>
      !params
        ? (["roles", "search"] as const)
        : (["roles", "search", params] as const),
    detail: (roleId: string) => ["roles", "detail", roleId] as const,
    members: (roleId: string, params?: unknown) =>
      !params
        ? (["roles", "members", roleId] as const)
        : (["roles", "members", roleId, params] as const),
    groups: (roleId: string, params?: unknown) =>
      !params
        ? (["roles", "groups", roleId] as const)
        : (["roles", "groups", roleId, params] as const),
    mappingRules: (roleId: string, params?: unknown) =>
      !params
        ? (["roles", "mappingRules", roleId] as const)
        : (["roles", "mappingRules", roleId, params] as const),
    clients: (roleId: string, params?: unknown) =>
      !params
        ? (["roles", "clients", roleId] as const)
        : (["roles", "clients", roleId, params] as const),
  },
  tenants: {
    all: ["tenants"] as const,
    search: (params?: unknown) =>
      !params
        ? (["tenants", "search"] as const)
        : (["tenants", "search", params] as const),
    detail: (tenantId: string) => ["tenants", "detail", tenantId] as const,
    members: (tenantId: string, params?: unknown) =>
      !params
        ? (["tenants", "members", tenantId] as const)
        : (["tenants", "members", tenantId, params] as const),
    roles: (tenantId: string, params?: unknown) =>
      !params
        ? (["tenants", "roles", tenantId] as const)
        : (["tenants", "roles", tenantId, params] as const),
    groups: (tenantId: string, params?: unknown) =>
      !params
        ? (["tenants", "groups", tenantId] as const)
        : (["tenants", "groups", tenantId, params] as const),
    mappingRules: (tenantId: string, params?: unknown) =>
      !params
        ? (["tenants", "mappingRules", tenantId] as const)
        : (["tenants", "mappingRules", tenantId, params] as const),
    clients: (tenantId: string, params?: unknown) =>
      !params
        ? (["tenants", "clients", tenantId] as const)
        : (["tenants", "clients", tenantId, params] as const),
  },
  authorizations: {
    all: ["authorizations"] as const,
    search: (params?: unknown) =>
      !params
        ? (["authorizations", "search"] as const)
        : (["authorizations", "search", params] as const),
  },
  mappingRules: {
    all: ["mappingRules"] as const,
    search: (params?: unknown) =>
      !params
        ? (["mappingRules", "search"] as const)
        : (["mappingRules", "search", params] as const),
  },
  auditLogs: {
    all: ["auditLogs"] as const,
    search: (params?: unknown) =>
      !params
        ? (["auditLogs", "search"] as const)
        : (["auditLogs", "search", params] as const),
  },
  clusterVariables: {
    all: ["clusterVariables"] as const,
    search: (params?: unknown) =>
      !params
        ? (["clusterVariables", "search"] as const)
        : (["clusterVariables", "search", params] as const),
  },
  globalTaskListeners: {
    all: ["globalTaskListeners"] as const,
    search: (params?: unknown) =>
      !params
        ? (["globalTaskListeners", "search"] as const)
        : (["globalTaskListeners", "search", params] as const),
  },
  messageSubscriptions: {
    all: ["messageSubscriptions"] as const,
    search: (params?: unknown) =>
      !params
        ? (["messageSubscriptions", "search"] as const)
        : (["messageSubscriptions", "search", params] as const),
  },
  license: ["license"] as const,
  authentication: {
    me: ["authentication", "me"] as const,
  },
  setup: ["setup"] as const,
} as const;
