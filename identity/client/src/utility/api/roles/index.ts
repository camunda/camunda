/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  Group,
  MappingRule,
  QueryClientsByRoleRequestBody,
  QueryClientsByRoleResponseBody,
  QueryGroupsByRoleRequestBody,
  QueryGroupsByRoleResponseBody,
  QueryMappingRulesByRoleRequestBody,
  QueryMappingRulesResponseBody,
  QueryRolesRequestBody,
  QueryRolesResponseBody,
  Role,
  TenantClient,
} from "@camunda/camunda-api-zod-schemas/8.10";
import {
  ApiDefinition,
  apiDelete,
  apiGet,
  apiPost,
  apiPut,
} from "src/utility/api/request";

export const ROLES_ENDPOINT = "/roles";

export const searchRoles: ApiDefinition<
  QueryRolesResponseBody,
  QueryRolesRequestBody | undefined
> = (params) => apiPost(`${ROLES_ENDPOINT}/search`, params);

export const getRoleDetails: ApiDefinition<Role, Pick<Role, "roleId">> = ({
  roleId,
}) => apiGet(`${ROLES_ENDPOINT}/${encodeURIComponent(roleId)}`);

export const createRole: ApiDefinition<undefined, Role> = (role) =>
  apiPost(ROLES_ENDPOINT, role);

export const deleteRole: ApiDefinition<undefined, Pick<Role, "roleId">> = ({
  roleId,
}) => apiDelete(`${ROLES_ENDPOINT}/${encodeURIComponent(roleId)}`);

// ----------------- Mapping rules within a Role -----------------

export const getMappingRulesByRoleId: ApiDefinition<
  QueryMappingRulesResponseBody,
  QueryMappingRulesByRoleRequestBody & Pick<Role, "roleId">
> = (params) => {
  const { roleId, ...body } = params;
  return apiPost(
    `${ROLES_ENDPOINT}/${encodeURIComponent(roleId)}/mapping-rules/search`,
    body,
  );
};

export const assignRoleMappingRule: ApiDefinition<
  undefined,
  Pick<Role, "roleId"> & Pick<MappingRule, "mappingRuleId">
> = ({ roleId, mappingRuleId }) => {
  return apiPut(
    `${ROLES_ENDPOINT}/${encodeURIComponent(roleId)}/mapping-rules/${encodeURIComponent(mappingRuleId)}`,
  );
};

export const unassignRoleMappingRule: ApiDefinition<
  undefined,
  Pick<Role, "roleId"> & Pick<MappingRule, "mappingRuleId">
> = ({ roleId, mappingRuleId }) =>
  apiDelete(
    `${ROLES_ENDPOINT}/${encodeURIComponent(roleId)}/mapping-rules/${encodeURIComponent(mappingRuleId)}`,
  );

// ----------------- Groups within a Role -----------------

export const getGroupsByRoleId: ApiDefinition<
  QueryGroupsByRoleResponseBody,
  Pick<Role, "roleId"> & QueryGroupsByRoleRequestBody
> = ({ roleId, ...body }) =>
  apiPost(
    `${ROLES_ENDPOINT}/${encodeURIComponent(roleId)}/groups/search`,
    body,
  );

export const assignRoleGroup: ApiDefinition<
  undefined,
  Pick<Role, "roleId"> & Pick<Group, "groupId">
> = ({ roleId, groupId }) => {
  return apiPut(
    `${ROLES_ENDPOINT}/${encodeURIComponent(roleId)}/groups/${encodeURIComponent(groupId)}`,
  );
};

export const unassignRoleGroup: ApiDefinition<
  undefined,
  Pick<Role, "roleId"> & Pick<Group, "groupId">
> = ({ roleId, groupId }) =>
  apiDelete(
    `${ROLES_ENDPOINT}/${encodeURIComponent(roleId)}/groups/${encodeURIComponent(groupId)}`,
  );

// ----------------- Clients within a Role -----------------

export const getClientsByRoleId: ApiDefinition<
  QueryClientsByRoleResponseBody,
  QueryClientsByRoleRequestBody & Pick<Role, "roleId">
> = (args) => {
  const { roleId, ...body } = args;
  return apiPost(
    `${ROLES_ENDPOINT}/${encodeURIComponent(roleId)}/clients/search`,
    body,
  );
};

export const assignRoleClient: ApiDefinition<
  undefined,
  Pick<Role, "roleId"> & Pick<TenantClient, "clientId">
> = ({ roleId, clientId }) => {
  return apiPut(
    `${ROLES_ENDPOINT}/${encodeURIComponent(roleId)}/clients/${encodeURIComponent(clientId)}`,
  );
};

export const unassignRoleClient: ApiDefinition<
  undefined,
  Pick<Role, "roleId"> & Pick<TenantClient, "clientId">
> = ({ roleId, clientId }) =>
  apiDelete(
    `${ROLES_ENDPOINT}/${encodeURIComponent(roleId)}/clients/${encodeURIComponent(clientId)}`,
  );
