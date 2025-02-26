/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { ApiDefinition, apiDelete, apiGet, apiPost } from "../request";
import { User } from "src/utility/api/users";
import { GROUPS_ENDPOINT } from "src/utility/api/groups";
import { SearchResponse } from "src/utility/api";
import { TENANTS_ENDPOINT } from "src/utility/api/tenants";

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

type AssignGroupMemberParams = GetGroupMembersParams & { userId: string };
export const assignGroupMember: ApiDefinition<
  undefined,
  AssignGroupMemberParams
> = ({ groupId, userId }) =>
  apiPost(`${GROUPS_ENDPOINT}/${groupId}/users`, { id: userId });

type UnassignGroupMemberParams = AssignGroupMemberParams;
export const unassignGroupMember: ApiDefinition<
  undefined,
  UnassignGroupMemberParams
> = ({ groupId, userId }) =>
  apiDelete(`${GROUPS_ENDPOINT}/${groupId}/users/${userId}`);

type AssignTenantMemberParams = GetTenantMembersParams & { userId: string };
export const assignTenantMember: ApiDefinition<
  undefined,
  AssignTenantMemberParams
> = ({ tenantId, userId }) => {
  return apiPost(`${TENANTS_ENDPOINT}/${tenantId}/users`, { id: userId });
};

type UnassignTenantMemberParams = AssignTenantMemberParams;
export const unassignTenantMember: ApiDefinition<
  undefined,
  UnassignTenantMemberParams
> = ({ tenantId, userId }) =>
  apiDelete(`${TENANTS_ENDPOINT}/${tenantId}/users/${userId}`);
