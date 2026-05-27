/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  Group,
  QueryUsersByGroupRequestBody,
  QueryUsersByRoleRequestBody,
  QueryUsersByTenantRequestBody,
  Role,
  Tenant,
  User,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { getApiBaseUrl } from "src/configuration/urlConfig";
import { unwrap } from "../request";
import { queryKeys } from "../queryKeys";
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

// ----------------- Members of a Group -----------------

export const useGroupMembers = (
  groupId: string,
  params?: QueryUsersByGroupRequestBody | Record<string, unknown>,
) =>
  useQuery({
    queryKey: queryKeys.groups.members(groupId, params),
    queryFn: () =>
      unwrap(
        searchMembersByGroup({
          groupId,
          ...(params as QueryUsersByGroupRequestBody),
        })(getApiBaseUrl()),
      ),
    enabled: !!groupId,
  });

export const useAssignGroupMember = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<Group, "groupId"> & Pick<User, "username">) =>
      unwrap(assignGroupMember(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["groups", "members", variables.groupId],
      }),
  });
};

export const useUnassignGroupMember = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<Group, "groupId"> & Pick<User, "username">) =>
      unwrap(unassignGroupMember(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["groups", "members", variables.groupId],
      }),
  });
};

// ----------------- Members of a Tenant -----------------

export const useTenantMembers = (
  tenantId: string,
  params?: QueryUsersByTenantRequestBody | Record<string, unknown>,
) =>
  useQuery({
    queryKey: queryKeys.tenants.members(tenantId, params),
    queryFn: () =>
      unwrap(
        getMembersByTenantId({
          tenantId,
          ...(params as QueryUsersByTenantRequestBody),
        })(getApiBaseUrl()),
      ),
    enabled: !!tenantId,
  });

export const useAssignTenantMember = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<Tenant, "tenantId"> & Pick<User, "username">) =>
      unwrap(assignTenantMember(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["tenants", "members", variables.tenantId],
      }),
  });
};

export const useUnassignTenantMember = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<Tenant, "tenantId"> & Pick<User, "username">) =>
      unwrap(unassignTenantMember(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["tenants", "members", variables.tenantId],
      }),
  });
};

// ----------------- Members of a Role -----------------

export const useRoleMembers = (
  roleId: string,
  params?: QueryUsersByRoleRequestBody | Record<string, unknown>,
) =>
  useQuery({
    queryKey: queryKeys.roles.members(roleId, params),
    queryFn: () =>
      unwrap(
        getMembersByRole({
          roleId,
          ...(params as QueryUsersByRoleRequestBody),
        })(getApiBaseUrl()),
      ),
    enabled: !!roleId,
  });

export const useAssignRoleMember = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<Role, "roleId"> & Pick<User, "username">) =>
      unwrap(assignRoleMember(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["roles", "members", variables.roleId],
      }),
  });
};

export const useUnassignRoleMember = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<Role, "roleId"> & Pick<User, "username">) =>
      unwrap(unassignRoleMember(body)(getApiBaseUrl())),
    onSuccess: (_data, variables) =>
      qc.invalidateQueries({
        queryKey: ["roles", "members", variables.roleId],
      }),
  });
};
