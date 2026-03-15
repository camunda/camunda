/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  User,
  QueryUsersRequestBody,
  QueryUsersResponseBody,
  CreateUserRequestBody,
  UpdateUserRequestBody,
} from "@camunda/camunda-api-zod-schemas/8.9";

import { ApiDefinition, apiDelete, apiPost, apiPut } from "../request";

export type UserKeys = keyof User;

export const USERS_ENDPOINT = "/users";

export const searchUser: ApiDefinition<
  QueryUsersResponseBody,
  (QueryUsersRequestBody & { usernames?: string[] }) | undefined
> = (params = {}) => {
  const { usernames, ...restParams } = params;

  const filters: QueryUsersRequestBody | undefined = usernames
    ? { filter: { username: { $in: usernames } } }
    : undefined;

  return apiPost(`${USERS_ENDPOINT}/search`, { ...restParams, ...filters });
};

export const getUserDetails: ApiDefinition<
  QueryUsersResponseBody,
  Pick<User, "username">
> = ({ username }) =>
  apiPost(`${USERS_ENDPOINT}/search`, { filter: { username } });

export const createUser: ApiDefinition<undefined, CreateUserRequestBody> = (
  user,
) => apiPost(USERS_ENDPOINT, { ...user });

export const updateUser: ApiDefinition<
  undefined,
  UpdateUserRequestBody & Pick<User, "username">
> = (user) => {
  const { name, email, username, password } = user;
  return apiPut(`${USERS_ENDPOINT}/${encodeURIComponent(username)}`, {
    name,
    email,
    password,
  });
};

export const deleteUser: ApiDefinition<undefined, Pick<User, "username">> = ({
  username,
}) => apiDelete(`${USERS_ENDPOINT}/${encodeURIComponent(username)}`);
