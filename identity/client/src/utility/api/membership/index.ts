/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { ApiDefinition, apiDelete, apiGet, apiPost, apiPut } from "../request";
import { User } from "src/utility/api/users";
import { GROUPS_ENDPOINT } from "src/utility/api/groups";
import { SearchResponse } from "src/utility/api";
import { TENANTS_ENDPOINT } from "src/utility/api/tenants";
import { ROLES_ENDPOINT } from "src/utility/api/roles";

export type GetGroupMembersParams = {
  groupId: string;
};
export const getMembersByGroup: ApiDefinition<
  SearchResponse<User>,
  GetGroupMembersParams
> = ({ groupId }) => apiGet(`${GROUPS_ENDPOINT}/${groupId}/users`);

export type GetTenantMembersParams = {
  tenantId: string;
};
export const getMembersByTenantId: ApiDefinition<
  SearchResponse<User>,
  GetTenantMembersParams
> = ({ tenantId }) => apiPost(`${TENANTS_ENDPOINT}/${tenantId}/users/search`);

export type GetRoleMembersParams = {
  roleId: string;
};
export const getMembersByRole: ApiDefinition<
  SearchResponse<User>,
  GetRoleMembersParams
> = ({ roleId }) => apiPost(`${ROLES_ENDPOINT}/${roleId}/users/search`);

type AssignGroupMemberParams = GetGroupMembersParams & { username: string };
export const assignGroupMember: ApiDefinition<
  undefined,
  AssignGroupMemberParams
> = ({ groupId, username }) =>
  apiPost(`${GROUPS_ENDPOINT}/${groupId}/users/${username}`);

type UnassignGroupMemberParams = AssignGroupMemberParams;
export const unassignGroupMember: ApiDefinition<
  undefined,
  UnassignGroupMemberParams
> = ({ groupId, username }) =>
  apiDelete(`${GROUPS_ENDPOINT}/${groupId}/users/${username}`);

type AssignTenantMemberParams = GetTenantMembersParams & { username: string };
export const assignTenantMember: ApiDefinition<
  undefined,
  AssignTenantMemberParams
> = ({ tenantId, username }) => {
  return apiPut(`${TENANTS_ENDPOINT}/${tenantId}/users/${username}`);
};

type UnassignTenantMemberParams = AssignTenantMemberParams;
export const unassignTenantMember: ApiDefinition<
  undefined,
  UnassignTenantMemberParams
> = ({ tenantId, username }) =>
  apiDelete(`${TENANTS_ENDPOINT}/${tenantId}/users/${username}`);

type AssignRoleMemberParams = GetRoleMembersParams & { username: string };
export const assignRoleMember: ApiDefinition<
  undefined,
  AssignRoleMemberParams
> = ({ roleId, username }) => {
  return apiPut(`${ROLES_ENDPOINT}/${roleId}/users/${username}`);
};

type UnassignRoleMemberParams = AssignRoleMemberParams;
export const unassignRoleMember: ApiDefinition<
  undefined,
  UnassignRoleMemberParams
> = ({ roleId, username }) =>
  apiDelete(`${ROLES_ENDPOINT}/${roleId}/users/${username}`);
