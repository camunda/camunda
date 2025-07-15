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
import { Mapping } from "src/utility/api/mappings";

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

export type GetRoleMappingsParams = {
  roleId: string;
};
export const getMappingsByRoleId: ApiDefinition<
  SearchResponse<Mapping>,
  GetRoleMappingsParams
> = ({ roleId }) => apiPost(`${ROLES_ENDPOINT}/${roleId}/mapping-rules/search`);

type AssignRoleMappingParams = GetRoleMappingsParams & { mappingId: string };
export const assignRoleMapping: ApiDefinition<
  undefined,
  AssignRoleMappingParams
> = ({ roleId, mappingId }) => {
  return apiPut(`${ROLES_ENDPOINT}/${roleId}/mappings/${mappingId}`);
};

type UnassignRoleMappingParams = AssignRoleMappingParams;
export const unassignRoleMapping: ApiDefinition<
  undefined,
  UnassignRoleMappingParams
> = ({ roleId, mappingId }) =>
  apiDelete(`${ROLES_ENDPOINT}/${roleId}/mappings/${mappingId}`);

// ----------------- Groups within a Role -----------------

type GetRoleGroupsParams = {
  roleId: string;
};

export const getGroupsByRoleId: ApiDefinition<
  SearchResponse<Group>,
  GetRoleGroupsParams
> = ({ roleId }) => apiPost(`${ROLES_ENDPOINT}/${roleId}/groups/search`);

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
