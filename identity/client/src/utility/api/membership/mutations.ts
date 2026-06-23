/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { mutationOptions, QueryClient } from "@tanstack/react-query";
import type {
  Group,
  Role,
  Tenant,
  User,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { getApiBaseUrl } from "src/configuration/urlConfig";
import { unwrap } from "../request";
import {
  assignGroupMember,
  assignRoleMember,
  assignTenantMember,
  unassignGroupMember,
  unassignRoleMember,
  unassignTenantMember,
} from ".";

export const membershipMutations = {
  assignGroupMember: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Group, "groupId"> & Pick<User, "username">) =>
        unwrap(assignGroupMember(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: ["groups", "members", variables.groupId],
        }),
    }),
  unassignGroupMember: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Group, "groupId"> & Pick<User, "username">) =>
        unwrap(unassignGroupMember(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: ["groups", "members", variables.groupId],
        }),
    }),
  assignTenantMember: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Tenant, "tenantId"> & Pick<User, "username">) =>
        unwrap(assignTenantMember(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: ["tenants", "members", variables.tenantId],
        }),
    }),
  unassignTenantMember: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Tenant, "tenantId"> & Pick<User, "username">) =>
        unwrap(unassignTenantMember(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: ["tenants", "members", variables.tenantId],
        }),
    }),
  assignRoleMember: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Role, "roleId"> & Pick<User, "username">) =>
        unwrap(assignRoleMember(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: ["roles", "members", variables.roleId],
        }),
    }),
  unassignRoleMember: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Role, "roleId"> & Pick<User, "username">) =>
        unwrap(unassignRoleMember(body)(getApiBaseUrl())),
      onSuccess: (_data, variables) =>
        qc.invalidateQueries({
          queryKey: ["roles", "members", variables.roleId],
        }),
    }),
};
