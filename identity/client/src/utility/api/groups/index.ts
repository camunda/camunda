/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { ApiDefinition, apiDelete, apiGet, apiPost, apiPut } from "../request";

export const GROUPS_ENDPOINT = "/groups";

export type Group = {
  id: string;
  name: string;
};

export const getGroups: ApiDefinition<Group[]> = () => apiGet(GROUPS_ENDPOINT);

export type GetGroupParams = {
  id: string;
};

export const getGroupDetails: ApiDefinition<Group, GetGroupParams> = ({ id }) =>
  apiGet(`${GROUPS_ENDPOINT}/${id}`);

export type CreateGroupParams = { name: Group["name"] };

export const createGroup: ApiDefinition<undefined, CreateGroupParams> = (
  params,
) => apiPost(GROUPS_ENDPOINT, params);

export const updateGroup: ApiDefinition<undefined, Group> = ({ id, name }) =>
  apiPut(`${GROUPS_ENDPOINT}/${id}`, { name });

type DeleteGroupParams = GetGroupParams;

export const deleteGroup: ApiDefinition<undefined, DeleteGroupParams> = ({
  id,
}) => apiDelete(`${GROUPS_ENDPOINT}/${id}`);
