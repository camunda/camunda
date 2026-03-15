/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  Group,
  QueryUsersByGroupResponseBody,
  QueryUsersByTenantRequestBody,
  QueryUsersByTenantResponseBody,
  QueryUsersByGroupRequestBody,
  QueryUsersByRoleRequestBody,
  QueryUsersByRoleResponseBody,
  Role,
  Tenant,
  User,
} from "@camunda/camunda-api-zod-schemas/8.9";
import { ApiDefinition, apiDelete, apiPost, apiPut } from "../request";
import { GROUPS_ENDPOINT } from "src/utility/api/groups";
import { TENANTS_ENDPOINT } from "src/utility/api/tenants";
import { ROLES_ENDPOINT } from "src/utility/api/roles";

export const searchMembersByGroup: ApiDefinition<
  QueryUsersByGroupResponseBody,
  QueryUsersByGroupRequestBody & Pick<Group, "groupId">
> = ({ groupId, ...body }) =>
  apiPost(
    `${GROUPS_ENDPOINT}/${encodeURIComponent(groupId)}/users/search`,
    body,
  );

export const getMembersByTenantId: ApiDefinition<
  QueryUsersByTenantResponseBody,
  QueryUsersByTenantRequestBody & Pick<Tenant, "tenantId">
> = ({ tenantId, ...body }) =>
  apiPost(
    `${TENANTS_ENDPOINT}/${encodeURIComponent(tenantId)}/users/search`,
    body,
  );

export const getMembersByRole: ApiDefinition<
  QueryUsersByRoleResponseBody,
  QueryUsersByRoleRequestBody & Pick<Role, "roleId">
> = ({ roleId, ...body }) =>
  apiPost(`${ROLES_ENDPOINT}/${encodeURIComponent(roleId)}/users/search`, body);

export const assignGroupMember: ApiDefinition<
  undefined,
  Pick<Group, "groupId"> & Pick<User, "username">
> = ({ groupId, username }) =>
  apiPut(
    `${GROUPS_ENDPOINT}/${encodeURIComponent(groupId)}/users/${encodeURIComponent(username)}`,
  );

export const unassignGroupMember: ApiDefinition<
  undefined,
  Pick<Group, "groupId"> & Pick<User, "username">
> = ({ groupId, username }) =>
  apiDelete(
    `${GROUPS_ENDPOINT}/${encodeURIComponent(groupId)}/users/${encodeURIComponent(username)}`,
  );

export const assignTenantMember: ApiDefinition<
  undefined,
  Pick<Tenant, "tenantId"> & Pick<User, "username">
> = ({ tenantId, username }) => {
  return apiPut(
    `${TENANTS_ENDPOINT}/${encodeURIComponent(tenantId)}/users/${encodeURIComponent(username)}`,
  );
};

export const unassignTenantMember: ApiDefinition<
  undefined,
  Pick<Tenant, "tenantId"> & Pick<User, "username">
> = ({ tenantId, username }) =>
  apiDelete(
    `${TENANTS_ENDPOINT}/${encodeURIComponent(tenantId)}/users/${encodeURIComponent(username)}`,
  );

export const assignRoleMember: ApiDefinition<
  undefined,
  Pick<Role, "roleId"> & Pick<User, "username">
> = ({ roleId, username }) => {
  return apiPut(
    `${ROLES_ENDPOINT}/${encodeURIComponent(roleId)}/users/${encodeURIComponent(username)}`,
  );
};

export const unassignRoleMember: ApiDefinition<
  undefined,
  Pick<Role, "roleId"> & Pick<User, "username">
> = ({ roleId, username }) =>
  apiDelete(
    `${ROLES_ENDPOINT}/${encodeURIComponent(roleId)}/users/${encodeURIComponent(username)}`,
  );
