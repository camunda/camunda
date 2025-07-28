/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { PageSearchParams } from "../hooks/usePagination";
import { ApiDefinition, apiDelete, apiPost, apiPut } from "../request";
import { SearchResponse } from "src/utility/api";

export const USERS_ENDPOINT = "/users";

export type UserKeys = "name" | "username" | "email";

export type User = {
  name: string;
  username: string;
  email: string;
};

type SearchUserParams = {
  usernames: string[];
};

export const searchUser: ApiDefinition<
  SearchResponse<User>,
  Partial<SearchUserParams & PageSearchParams> | undefined
> = (params = {}) => {
  const { usernames, ...restParams } = params;

  const filters = usernames
    ? { filter: { username: { $in: usernames } } }
    : undefined;

  return apiPost(`${USERS_ENDPOINT}/search`, { ...restParams, ...filters });
};

type GetUserParams = {
  username: string;
};

export const getUserDetails: ApiDefinition<
  SearchResponse<User>,
  GetUserParams
> = ({ username }) =>
  apiPost(`${USERS_ENDPOINT}/search`, { filter: { username } });

type CreateUserParams = { password: string } & User;

export const createUser: ApiDefinition<undefined, CreateUserParams> = (user) =>
  apiPost(USERS_ENDPOINT, { ...user, enabled: true });

type UpdateUserParams = { password?: string } & User;

export const updateUser: ApiDefinition<undefined, UpdateUserParams> = (
  user,
) => {
  const { name, email, username, password } = user;
  return apiPut(`${USERS_ENDPOINT}/${username}`, {
    name,
    email,
    password: password ?? "",
  });
};

type DeleteUserParams = {
  username: string;
};

export const deleteUser: ApiDefinition<undefined, DeleteUserParams> = ({
  username,
}) => apiDelete(`${USERS_ENDPOINT}/${username}`);
