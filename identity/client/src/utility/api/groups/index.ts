/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { ApiDefinition, apiDelete, apiGet, apiPost, apiPut } from "../request";
import { SearchResponse } from "src/utility/api";
import { Role } from "src/utility/api/roles";
import { Mapping } from "src/utility/api/mappings";

export const GROUPS_ENDPOINT = "/groups";

export type Group = {
  groupId: string;
  name: string;
  description?: string;
};

export const searchGroups: ApiDefinition<SearchResponse<Group>> = () =>
  apiPost(`${GROUPS_ENDPOINT}/search`);

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
> = ({ groupId }) => apiPost(`${GROUPS_ENDPOINT}/${groupId}/roles/search`);

type AssignGroupRoleParams = GetGroupRolesParams & { roleId: string };
export const assignGroupRole: ApiDefinition<
  undefined,
  AssignGroupRoleParams
> = ({ groupId, roleId }) => {
  return apiPut(`${GROUPS_ENDPOINT}/${groupId}/roles/${roleId}`);
};

type UnassignGroupRoleParams = AssignGroupRoleParams;
export const unassignGroupRole: ApiDefinition<
  undefined,
  UnassignGroupRoleParams
> = ({ groupId, roleId }) =>
  apiDelete(`${GROUPS_ENDPOINT}/${groupId}/roles/${roleId}`);

// ----------------- Mappings within a Group -----------------

export type GetGroupMappingsParams = {
  groupId: string;
};
export const getMappingsByGroupId: ApiDefinition<
  SearchResponse<Mapping>,
  GetGroupMappingsParams
> = ({ groupId }) =>
  apiPost(`${GROUPS_ENDPOINT}/${groupId}/mapping-rules/search`);

type AssignGroupMappingParams = GetGroupMappingsParams & { mappingId: string };
export const assignGroupMapping: ApiDefinition<
  undefined,
  AssignGroupMappingParams
> = ({ groupId, mappingId }) => {
  return apiPut(`${GROUPS_ENDPOINT}/${groupId}/mapping-rules/${mappingId}`);
};

type UnassignGroupMappingParams = AssignGroupMappingParams;
export const unassignGroupMapping: ApiDefinition<
  undefined,
  UnassignGroupMappingParams
> = ({ groupId, mappingId }) =>
  apiDelete(`${GROUPS_ENDPOINT}/${groupId}/mapping-rules/${mappingId}`);
