/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { mutationOptions, QueryClient } from "@tanstack/react-query";
import type {
  CreateUserRequestBody,
  UpdateUserRequestBody,
  User,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { getApiBaseUrl } from "src/configuration/urlConfig";
import { unwrap } from "../request";
import { queryKeys } from "../queryKeys";
import {
  confirmThenInvalidate,
  insertIntoListCaches,
  removeFromListCaches,
  updateInListCaches,
} from "../optimisticListUpdates";
import { createUser, deleteUser, searchUser, updateUser } from ".";

const searchUserByUsername = (username: string) =>
  unwrap(
    searchUser({ usernames: [username], page: { limit: 1 } })(getApiBaseUrl()),
  );

export const userMutations = {
  create: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: CreateUserRequestBody) =>
        unwrap(createUser(body)(getApiBaseUrl())),
      meta: { skipErrorNotification: true },
      onSuccess: async (_data, { username, name, email }) => {
        await insertIntoListCaches<User>(
          qc,
          queryKeys.users.search(),
          { username, name, email },
          (item) => item.username === username,
        );
        void confirmThenInvalidate(
          qc,
          queryKeys.users.all,
          () => searchUserByUsername(username),
          (items) => items.some((item) => item.username === username),
        );
      },
    }),
  update: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: UpdateUserRequestBody & Pick<User, "username">) =>
        unwrap(updateUser(body)(getApiBaseUrl())),
      meta: { skipErrorNotification: true },
      onSuccess: async (_data, { username, name, email }) => {
        await updateInListCaches<User>(
          qc,
          queryKeys.users.search(),
          (item) => item.username === username,
          { name, email },
        );
        await updateInListCaches<User>(
          qc,
          queryKeys.users.detail(username),
          (item) => item.username === username,
          { name, email },
        );
        void confirmThenInvalidate(
          qc,
          queryKeys.users.all,
          () => searchUserByUsername(username),
          (items) =>
            items.some(
              (item) =>
                item.username === username &&
                item.name === name &&
                item.email === email,
            ),
        );
      },
    }),
  delete: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<User, "username">) =>
        unwrap(deleteUser(body)(getApiBaseUrl())),
      onSuccess: async (_data, { username }) => {
        await removeFromListCaches<User>(
          qc,
          queryKeys.users.search(),
          (item) => item.username === username,
        );
        void confirmThenInvalidate(
          qc,
          queryKeys.users.all,
          () => searchUserByUsername(username),
          (items) => items.every((item) => item.username !== username),
        );
      },
    }),
};
