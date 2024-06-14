/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
import { ApiDefinition, apiGet } from "../request";
import { User } from "src/utility/api/users";

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

export type GetGroupUsersParams = {
  id: string;
};

export const getGroupUsers: ApiDefinition<User[], GetGroupUsersParams> = ({
  id,
}) => apiGet(`${GROUPS_ENDPOINT}/${id}/users`);
