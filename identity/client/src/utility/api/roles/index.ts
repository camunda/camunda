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
import { Mapping } from "src/utility/api/mappings";

export const ROLES_ENDPOINT = "/roles";

export type Role = {
  roleKey: string;
  name: string;
  description: string;
};

export const searchRoles: ApiDefinition<SearchResponse<Role>> = () =>
  apiPost(`${ROLES_ENDPOINT}/search`);

type GetRoleParams = {
  roleKey: string;
};
export const getRoleDetails: ApiDefinition<Role, GetRoleParams> = ({
  roleKey,
}) => apiGet(`${ROLES_ENDPOINT}/${roleKey}`);

type CreateRoleParams = Omit<Role, "roleKey">;
export const createRole: ApiDefinition<Role, CreateRoleParams> = (role) =>
  apiPost(ROLES_ENDPOINT, role);

export type DeleteRoleParams = {
  roleKey: string;
  name: string;
};
export const deleteRole: ApiDefinition<undefined, { roleKey: string }> = ({
  roleKey,
}) => apiDelete(`${ROLES_ENDPOINT}/${roleKey}`);

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
  return apiPut(`${ROLES_ENDPOINT}/${roleId}/mapping-rules/${mappingId}`);
};

type UnassignRoleMappingParams = AssignRoleMappingParams;
export const unassignRoleMapping: ApiDefinition<
  undefined,
  UnassignRoleMappingParams
> = ({ roleId, mappingId }) =>
  apiDelete(`${ROLES_ENDPOINT}/${roleId}/mapping-rules/${mappingId}`);
