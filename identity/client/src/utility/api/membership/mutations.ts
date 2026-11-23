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
import { syncSubTable } from "../optimisticListUpdates";
import {
  assignGroupMember,
  assignRoleMember,
  assignTenantMember,
  getMembersByRole,
  getMembersByTenantId,
  searchMembersByGroup,
  unassignGroupMember,
  unassignRoleMember,
  unassignTenantMember,
} from ".";

const membersOf = (parent: "groups" | "tenants" | "roles", id: string) => {
  const scope =
    parent === "groups"
      ? { groupId: id }
      : parent === "tenants"
        ? { tenantId: id }
        : { roleId: id };
  return {
    searchKey: ["enrichedMembers", parent] as const,
    scope,
    search: () =>
      unwrap(
        parent === "groups"
          ? searchMembersByGroup({ groupId: id, page: { limit: 1 } })(
              getApiBaseUrl(),
            )
          : parent === "tenants"
            ? getMembersByTenantId({ tenantId: id, page: { limit: 1 } })(
                getApiBaseUrl(),
              )
            : getMembersByRole({ roleId: id, page: { limit: 1 } })(
                getApiBaseUrl(),
              ),
      ),
  };
};

export const membershipMutations = {
  assignGroupMember: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Group, "groupId"> & Pick<User, "username">) =>
        unwrap(assignGroupMember(body)(getApiBaseUrl())),
      onSuccess: (_data, { groupId, username }) => {
        syncSubTable(qc, membersOf("groups", groupId), {
          type: "assign",
          item: { username },
          matches: (item) => item.username === username,
        });
      },
    }),
  unassignGroupMember: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Group, "groupId"> & Pick<User, "username">) =>
        unwrap(unassignGroupMember(body)(getApiBaseUrl())),
      onSuccess: (_data, { groupId, username }) => {
        syncSubTable(qc, membersOf("groups", groupId), {
          type: "unassign",
          matches: (item) => item.username === username,
        });
      },
    }),
  assignTenantMember: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Tenant, "tenantId"> & Pick<User, "username">) =>
        unwrap(assignTenantMember(body)(getApiBaseUrl())),
      onSuccess: (_data, { tenantId, username }) => {
        syncSubTable(qc, membersOf("tenants", tenantId), {
          type: "assign",
          item: { username },
          matches: (item) => item.username === username,
        });
      },
    }),
  unassignTenantMember: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Tenant, "tenantId"> & Pick<User, "username">) =>
        unwrap(unassignTenantMember(body)(getApiBaseUrl())),
      onSuccess: (_data, { tenantId, username }) => {
        syncSubTable(qc, membersOf("tenants", tenantId), {
          type: "unassign",
          matches: (item) => item.username === username,
        });
      },
    }),
  assignRoleMember: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Role, "roleId"> & Pick<User, "username">) =>
        unwrap(assignRoleMember(body)(getApiBaseUrl())),
      onSuccess: (_data, { roleId, username }) => {
        syncSubTable(qc, membersOf("roles", roleId), {
          type: "assign",
          item: { username },
          matches: (item) => item.username === username,
        });
      },
    }),
  unassignRoleMember: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<Role, "roleId"> & Pick<User, "username">) =>
        unwrap(unassignRoleMember(body)(getApiBaseUrl())),
      onSuccess: (_data, { roleId, username }) => {
        syncSubTable(qc, membersOf("roles", roleId), {
          type: "unassign",
          matches: (item) => item.username === username,
        });
      },
    }),
};
