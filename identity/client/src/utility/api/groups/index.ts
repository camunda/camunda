/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  MappingRule,
  Role,
  Group,
  TenantClient,
  QueryGroupsRequestBody,
  QueryGroupsResponseBody,
  QueryRolesByGroupRequestBody,
  QueryRolesByGroupResponseBody,
  QueryMappingRulesByGroupRequestBody,
  QueryMappingRulesByGroupResponseBody,
  QueryClientsByGroupRequestBody,
  QueryClientsByGroupResponseBody,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { ApiDefinition, apiDelete, apiGet, apiPost, apiPut } from "../request";
import { ROLES_ENDPOINT } from "src/utility/api/roles";

export type GroupKeys = keyof Group;

export const GROUPS_ENDPOINT = "/groups";

export const searchGroups: ApiDefinition<
  QueryGroupsResponseBody,
  (QueryGroupsRequestBody & { groupIds?: string[] }) | undefined
> = (filterParams = {}) => {
  const { groupIds, ...pageParams } = filterParams;

  const params = groupIds
    ? { filter: { groupId: { $in: groupIds } } }
    : undefined;

  return apiPost(`${GROUPS_ENDPOINT}/search`, { ...params, ...pageParams });
};

export const getGroupDetails: ApiDefinition<Group, Pick<Group, "groupId">> = ({
  groupId,
}) => apiGet(`${GROUPS_ENDPOINT}/${encodeURIComponent(groupId)}`);

export const createGroup: ApiDefinition<undefined, Group> = (params) =>
  apiPost(GROUPS_ENDPOINT, params);

export const updateGroup: ApiDefinition<undefined, Group> = (group) => {
  const { groupId, name, description } = group;
  return apiPut(`${GROUPS_ENDPOINT}/${encodeURIComponent(groupId)}`, {
    name,
    description,
  });
};

export const deleteGroup: ApiDefinition<undefined, Pick<Group, "groupId">> = ({
  groupId,
}) => apiDelete(`${GROUPS_ENDPOINT}/${encodeURIComponent(groupId)}`);

// ----------------- Roles within a Group -----------------

export const searchRolesByGroupId: ApiDefinition<
  QueryRolesByGroupResponseBody,
  QueryRolesByGroupRequestBody & Pick<Group, "groupId">
> = ({ groupId, ...body }) =>
  apiPost(
    `${GROUPS_ENDPOINT}/${encodeURIComponent(groupId)}/roles/search`,
    body,
  );

export const assignGroupRole: ApiDefinition<
  undefined,
  Pick<Group, "groupId"> & Pick<Role, "roleId">
> = ({ groupId, roleId }) => {
  return apiPut(
    `${ROLES_ENDPOINT}/${encodeURIComponent(roleId)}/groups/${encodeURIComponent(groupId)}`,
  );
};

export const unassignGroupRole: ApiDefinition<
  undefined,
  Pick<Group, "groupId"> & Pick<Role, "roleId">
> = ({ groupId, roleId }) =>
  apiDelete(
    `${ROLES_ENDPOINT}/${encodeURIComponent(roleId)}/groups/${encodeURIComponent(groupId)}`,
  );

// ----------------- Mapping rules within a Group -----------------

export const getMappingRulesByGroupId: ApiDefinition<
  QueryMappingRulesByGroupResponseBody,
  QueryMappingRulesByGroupRequestBody & Pick<Group, "groupId">
> = (params) => {
  const { groupId, ...body } = params;
  return apiPost(
    `${GROUPS_ENDPOINT}/${encodeURIComponent(groupId)}/mapping-rules/search`,
    body,
  );
};

export const assignGroupMappingRule: ApiDefinition<
  undefined,
  Pick<Group, "groupId"> & Pick<MappingRule, "mappingRuleId">
> = ({ groupId, mappingRuleId }) => {
  return apiPut(
    `${GROUPS_ENDPOINT}/${encodeURIComponent(groupId)}/mapping-rules/${encodeURIComponent(mappingRuleId)}`,
  );
};

export const unassignGroupMappingRule: ApiDefinition<
  undefined,
  Pick<Group, "groupId"> & Pick<MappingRule, "mappingRuleId">
> = ({ groupId, mappingRuleId }) =>
  apiDelete(
    `${GROUPS_ENDPOINT}/${encodeURIComponent(groupId)}/mapping-rules/${encodeURIComponent(mappingRuleId)}`,
  );

export const getClientsByGroupId: ApiDefinition<
  QueryClientsByGroupResponseBody,
  QueryClientsByGroupRequestBody & Pick<Group, "groupId">
> = (args) => {
  const { groupId, ...body } = args;
  return apiPost(
    `${GROUPS_ENDPOINT}/${encodeURIComponent(groupId)}/clients/search`,
    body,
  );
};

export const assignGroupClient: ApiDefinition<
  undefined,
  Pick<Group, "groupId"> & Pick<TenantClient, "clientId">
> = ({ groupId, clientId }) => {
  return apiPut(
    `${GROUPS_ENDPOINT}/${encodeURIComponent(groupId)}/clients/${encodeURIComponent(clientId)}`,
  );
};

export const unassignGroupClient: ApiDefinition<
  undefined,
  Pick<Group, "groupId"> & Pick<TenantClient, "clientId">
> = ({ groupId, clientId }) =>
  apiDelete(
    `${GROUPS_ENDPOINT}/${encodeURIComponent(groupId)}/clients/${encodeURIComponent(clientId)}`,
  );
