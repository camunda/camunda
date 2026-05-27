/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import type {
  CreateUserRequestBody,
  QueryUsersRequestBody,
  UpdateUserRequestBody,
  User,
} from "@camunda/camunda-api-zod-schemas/8.10";
import { getApiBaseUrl } from "src/configuration/urlConfig";
import { unwrap } from "../request";
import { queryKeys } from "../queryKeys";
import {
  createUser,
  deleteUser,
  getUserDetails,
  searchUser,
  updateUser,
} from ".";

type SearchUsersParams = QueryUsersRequestBody & { usernames?: string[] };

export const useSearchUsers = (
  params?: SearchUsersParams | Record<string, unknown>,
  options?: { enabled?: boolean },
) =>
  useQuery({
    queryKey: queryKeys.users.search(params),
    queryFn: () =>
      unwrap(searchUser(params as SearchUsersParams)(getApiBaseUrl())),
    enabled: options?.enabled,
  });

export const useUserDetails = (username: string | undefined) =>
  useQuery({
    queryKey: queryKeys.users.detail(username ?? ""),
    queryFn: () =>
      unwrap(getUserDetails({ username: username as string })(getApiBaseUrl())),
    enabled: !!username,
  });

export const useCreateUser = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: CreateUserRequestBody) =>
      unwrap(createUser(body)(getApiBaseUrl())),
    meta: { skipErrorNotification: true },
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.users.all }),
  });
};

export const useUpdateUser = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: UpdateUserRequestBody & Pick<User, "username">) =>
      unwrap(updateUser(body)(getApiBaseUrl())),
    meta: { skipErrorNotification: true },
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.users.all }),
  });
};

export const useDeleteUser = () => {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (body: Pick<User, "username">) =>
      unwrap(deleteUser(body)(getApiBaseUrl())),
    onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.users.all }),
  });
};
