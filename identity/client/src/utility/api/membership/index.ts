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

export type GetGroupMembersParams = {
  groupId: string;
};

export const getGroupMembers: ApiDefinition<User[], GetGroupMembersParams> = ({
  groupId,
}) => apiGet(`${GROUPS_ENDPOINT}/${groupId}/users`);

export const getMembersByGroup: ApiDefinition<
  SearchResponse<User>,
  GetGroupMembersParams
> = ({ groupId }) => apiGet(`${GROUPS_ENDPOINT}/${groupId}/users`);

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
