/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  ApiDefinition,
  apiDelete,
  apiGet,
  apiPost,
  apiPut,
} from "src/utility/api/request";
import { SearchResponse } from "src/utility/api";
import { Group } from "src/utility/api/groups";
import { MappingRule } from "src/utility/api/mapping-rules";
import { PageSearchParams } from "../hooks/usePagination";

export const ROLES_ENDPOINT = "/roles";

export type Role = {
  roleId: string;
  name: string;
  description: string;
};

export const searchRoles: ApiDefinition<
  SearchResponse<Role>,
  PageSearchParams | Record<string, unknown> | undefined
> = (params) => apiPost(`${ROLES_ENDPOINT}/search`, params);

type GetRoleParams = {
  roleId: string;
};
export const getRoleDetails: ApiDefinition<Role, GetRoleParams> = ({
  roleId,
}) => apiGet(`${ROLES_ENDPOINT}/${roleId}`);

export const createRole: ApiDefinition<undefined, Role> = (role) =>
  apiPost(ROLES_ENDPOINT, role);

export type DeleteRoleParams = {
  roleId: string;
  name: string;
};
export const deleteRole: ApiDefinition<undefined, { roleId: string }> = ({
  roleId,
}) => apiDelete(`${ROLES_ENDPOINT}/${roleId}`);

// ----------------- Mapping rules within a Role -----------------

export type GetRoleMappingRulesParams = {
  roleId: string;
};
export const getMappingRulesByRoleId: ApiDefinition<
  SearchResponse<MappingRule>,
  GetRoleMappingRulesParams
> = (params) => {
  const { roleId, ...body } = params;
  return apiPost(`${ROLES_ENDPOINT}/${roleId}/mapping-rules/search`, body);
};

type AssignRoleMappingParams = GetRoleMappingRulesParams & {
  mappingRuleId: string;
};
export const assignRoleMappingRule: ApiDefinition<
  undefined,
  AssignRoleMappingParams
> = ({ roleId, mappingRuleId }) => {
  return apiPut(`${ROLES_ENDPOINT}/${roleId}/mapping-rules/${mappingRuleId}`);
};

type UnassignRoleMappingParams = AssignRoleMappingParams;
export const unassignRoleMappingRule: ApiDefinition<
  undefined,
  UnassignRoleMappingParams
> = ({ roleId, mappingRuleId }) =>
  apiDelete(`${ROLES_ENDPOINT}/${roleId}/mapping-rules/${mappingRuleId}`);

// ----------------- Groups within a Role -----------------

type GetRoleGroupsParams = {
  roleId: string;
};

export const getGroupsByRoleId: ApiDefinition<
  SearchResponse<Group>,
  GetRoleGroupsParams
> = ({ roleId, ...body }) =>
  apiPost(`${ROLES_ENDPOINT}/${roleId}/groups/search`, body);

type AssignRoleGroupParams = GetRoleGroupsParams & Pick<Group, "groupId">;
export const assignRoleGroup: ApiDefinition<
  undefined,
  AssignRoleGroupParams
> = ({ roleId, groupId }) => {
  return apiPut(`${ROLES_ENDPOINT}/${roleId}/groups/${groupId}`);
};

type UnassignRoleGroupParams = AssignRoleGroupParams;
export const unassignRoleGroup: ApiDefinition<
  undefined,
  UnassignRoleGroupParams
> = ({ roleId, groupId }) =>
  apiDelete(`${ROLES_ENDPOINT}/${roleId}/groups/${groupId}`);

// ----------------- Clients within a Role -----------------

type GetRoleClientsParams = {
  roleId: string;
};

export type Client = {
  clientId: string;
};

export const getClientsByRoleId: ApiDefinition<
  SearchResponse<Client>,
  GetRoleClientsParams
> = (args) => {
  const { roleId, ...body } = args;
  return apiPost(`${ROLES_ENDPOINT}/${roleId}/clients/search`, body);
};

type AssignRoleClientParams = GetRoleClientsParams & Client;
export const assignRoleClient: ApiDefinition<
  undefined,
  AssignRoleClientParams
> = ({ roleId, clientId }) => {
  return apiPut(`${ROLES_ENDPOINT}/${roleId}/clients/${clientId}`);
};

type UnassignRoleClientParams = AssignRoleClientParams;
export const unassignRoleClient: ApiDefinition<
  undefined,
  UnassignRoleClientParams
> = ({ roleId, clientId }) =>
  apiDelete(`${ROLES_ENDPOINT}/${roleId}/clients/${clientId}`);
