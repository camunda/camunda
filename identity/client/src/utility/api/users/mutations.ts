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
import { createUser, deleteUser, updateUser } from ".";

export const userMutations = {
  create: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: CreateUserRequestBody) =>
        unwrap(createUser(body)(getApiBaseUrl())),
      meta: { skipErrorNotification: true },
      onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.users.all }),
    }),
  update: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: UpdateUserRequestBody & Pick<User, "username">) =>
        unwrap(updateUser(body)(getApiBaseUrl())),
      meta: { skipErrorNotification: true },
      onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.users.all }),
    }),
  delete: (qc: QueryClient) =>
    mutationOptions({
      mutationFn: (body: Pick<User, "username">) =>
        unwrap(deleteUser(body)(getApiBaseUrl())),
      onSuccess: () => qc.invalidateQueries({ queryKey: queryKeys.users.all }),
    }),
};
