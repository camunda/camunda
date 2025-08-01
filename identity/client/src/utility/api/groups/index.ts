/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { ApiDefinition, apiDelete, apiGet, apiPost, apiPut } from "../request";
import { SearchResponse } from "src/utility/api";
import { Role, ROLES_ENDPOINT } from "src/utility/api/roles";
import { MappingRule } from "src/utility/api/mapping-rules";
import { PageSearchParams } from "../hooks/usePagination";

export const GROUPS_ENDPOINT = "/groups";

export type GroupKeys = "groupId" | "name" | "description";

export type Group = {
  groupId: string;
  name: string;
  description?: string;
};

export type MemberGroup = Pick<Group, "groupId">;

type SearchGroupsParams = {
  groupIds: string[];
};

export const searchGroups: ApiDefinition<
  SearchResponse<Group>,
  Partial<SearchGroupsParams & PageSearchParams> | undefined
> = (filterParams = {}) => {
  const { groupIds, ...pageParams } = filterParams;

  const params = groupIds
    ? { filter: { groupId: { $in: groupIds } } }
    : undefined;

  return apiPost(`${GROUPS_ENDPOINT}/search`, { ...params, ...pageParams });
};

export type GetGroupParams = {
  groupId: string;
};

export const getGroupDetails: ApiDefinition<Group, GetGroupParams> = ({
  groupId,
}) => apiGet(`${GROUPS_ENDPOINT}/${groupId}`);

export const createGroup: ApiDefinition<undefined, Group> = (params) =>
  apiPost(GROUPS_ENDPOINT, params);

export const updateGroup: ApiDefinition<undefined, Group> = (group) => {
  const { groupId, name, description } = group;
  return apiPut(`${GROUPS_ENDPOINT}/${groupId}`, {
    name,
    description,
  });
};

type DeleteGroupParams = GetGroupParams;

export const deleteGroup: ApiDefinition<undefined, DeleteGroupParams> = ({
  groupId,
}) => apiDelete(`${GROUPS_ENDPOINT}/${groupId}`);

// ----------------- Roles within a Group -----------------

export type GetGroupRolesParams = {
  groupId: string;
};
export const searchRolesByGroupId: ApiDefinition<
  SearchResponse<Role>,
  GetGroupRolesParams
> = ({ groupId, ...body }) =>
  apiPost(`${GROUPS_ENDPOINT}/${groupId}/roles/search`, body);

type AssignGroupRoleParams = GetGroupRolesParams & { roleId: string };
export const assignGroupRole: ApiDefinition<
  undefined,
  AssignGroupRoleParams
> = ({ groupId, roleId }) => {
  return apiPut(`${ROLES_ENDPOINT}/${roleId}/groups/${groupId}`);
};

type UnassignGroupRoleParams = AssignGroupRoleParams;
export const unassignGroupRole: ApiDefinition<
  undefined,
  UnassignGroupRoleParams
> = ({ groupId, roleId }) =>
  apiDelete(`${ROLES_ENDPOINT}/${roleId}/groups/${groupId}`);

// ----------------- Mapping rules within a Group -----------------

export type GetGroupMappingRulesParams = {
  groupId: string;
};
export const getMappingRulesByGroupId: ApiDefinition<
  SearchResponse<MappingRule>,
  GetGroupMappingRulesParams
> = (params) => {
  const { groupId, ...body } = params;
  return apiPost(`${GROUPS_ENDPOINT}/${groupId}/mapping-rules/search`, body);
};

type AssignGroupMappingRuleParams = GetGroupMappingRulesParams & {
  mappingRuleId: string;
};
export const assignGroupMappingRule: ApiDefinition<
  undefined,
  AssignGroupMappingRuleParams
> = ({ groupId, mappingRuleId }) => {
  return apiPut(`${GROUPS_ENDPOINT}/${groupId}/mapping-rules/${mappingRuleId}`);
};

type UnassignGroupMappingRuleParams = AssignGroupMappingRuleParams;
export const unassignGroupMappingRule: ApiDefinition<
  undefined,
  UnassignGroupMappingRuleParams
> = ({ groupId, mappingRuleId }) =>
  apiDelete(`${GROUPS_ENDPOINT}/${groupId}/mapping-rules/${mappingRuleId}`);

type GetGroupClientsParams = {
  groupId: string;
};

export type Client = {
  clientId: string;
};

export const getClientsByGroupId: ApiDefinition<
  SearchResponse<Client>,
  GetGroupClientsParams
> = (args) => {
  const { groupId, ...body } = args;
  return apiPost(`${GROUPS_ENDPOINT}/${groupId}/clients/search`, body);
};

type AssignGroupClientParams = GetGroupClientsParams & Client;
export const assignGroupClient: ApiDefinition<
  undefined,
  AssignGroupClientParams
> = ({ groupId, clientId }) => {
  return apiPut(`${GROUPS_ENDPOINT}/${groupId}/clients/${clientId}`);
};

type UnassignGroupClientParams = AssignGroupClientParams;
export const unassignGroupClient: ApiDefinition<
  undefined,
  UnassignGroupClientParams
> = ({ groupId, clientId }) =>
  apiDelete(`${GROUPS_ENDPOINT}/${groupId}/clients/${clientId}`);
