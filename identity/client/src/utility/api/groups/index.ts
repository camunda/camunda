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
  apiPatch,
  apiPut,
} from "../request";
import { SearchResponse } from "src/utility/api";
import { Role } from "src/utility/api/roles";

export const GROUPS_ENDPOINT = "/groups";

export type Group = {
  groupKey: string;
  name: string;
  description?: string;
};

export const searchGroups: ApiDefinition<SearchResponse<Group>> = () =>
  apiPost(`${GROUPS_ENDPOINT}/search`);

export type GetGroupParams = {
  groupKey: string;
};

export const getGroupDetails: ApiDefinition<Group, GetGroupParams> = ({
  groupKey,
}) => apiGet(`${GROUPS_ENDPOINT}/${groupKey}`);

export type CreateGroupParams = { name: Group["name"] };

export const createGroup: ApiDefinition<undefined, CreateGroupParams> = (
  params,
) => apiPost(GROUPS_ENDPOINT, params);

export const updateGroup: ApiDefinition<undefined, Group> = (group) => {
  const { groupKey, name } = group;
  return apiPatch(`${GROUPS_ENDPOINT}/${groupKey}`, {
    changeset: { name },
  });
};

type DeleteGroupParams = GetGroupParams;

export const deleteGroup: ApiDefinition<undefined, DeleteGroupParams> = ({
  groupKey,
}) => apiDelete(`${GROUPS_ENDPOINT}/${groupKey}`);

// ----------------- Roles within a Group -----------------

export type GetGroupRolesParams = {
  groupId: string;
};
export const getRolesByGroupId: ApiDefinition<
  SearchResponse<Role>,
  GetGroupRolesParams
> = ({ groupId }) => apiPost(`${GROUPS_ENDPOINT}/${groupId}/roles/search`);

type AssignGroupRoleParams = GetGroupRolesParams & { key: number };
export const assignGroupRole: ApiDefinition<
  undefined,
  AssignGroupRoleParams
> = ({ groupId, key }) => {
  return apiPut(`${GROUPS_ENDPOINT}/${groupId}/roles/${key}`);
};

type UnassignGroupRoleParams = AssignGroupRoleParams;
export const unassignGroupRole: ApiDefinition<
  undefined,
  UnassignGroupRoleParams
> = ({ groupId, key }) =>
  apiDelete(`${GROUPS_ENDPOINT}/${groupId}/roles/${key}`);
