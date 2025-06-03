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
import { MappingRule } from "src/utility/api/mappings";

export const ROLES_ENDPOINT = "/roles";

export type Role = {
  roleId: string;
  name: string;
  description: string;
};

export const searchRoles: ApiDefinition<SearchResponse<Role>> = () =>
  apiPost(`${ROLES_ENDPOINT}/search`);

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

// ----------------- Mappings within a Role -----------------

export type GetRoleMappingRulesParams = {
  roleId: string;
};
export const getMappingRulesByRoleId: ApiDefinition<
  SearchResponse<MappingRule>,
  GetRoleMappingRulesParams
> = ({ roleId }) => apiPost(`${ROLES_ENDPOINT}/${roleId}/mapping-rules/search`);

type AssignRoleMappingRuleParams = GetRoleMappingRulesParams & {
  mappingRuleId: string;
};
export const assignRoleMapping: ApiDefinition<
  undefined,
  AssignRoleMappingRuleParams
> = ({ roleId, mappingRuleId }) => {
  return apiPut(`${ROLES_ENDPOINT}/${roleId}/mapping-rules/${mappingRuleId}`);
};

type UnassignRoleMappingRuleParams = AssignRoleMappingRuleParams;
export const unassignRoleMapping: ApiDefinition<
  undefined,
  UnassignRoleMappingRuleParams
> = ({ roleId, mappingRuleId }) =>
  apiDelete(`${ROLES_ENDPOINT}/${roleId}/mapping-rules/${mappingRuleId}`);

type GetRoleClientsParams = {
  roleId: string;
};

export type Client = {
  clientId: string;
};

export const getClientsByRoleId: ApiDefinition<
  SearchResponse<Client>,
  GetRoleClientsParams
> = ({ roleId }) => apiPost(`${ROLES_ENDPOINT}/${roleId}/clients/search`);

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
