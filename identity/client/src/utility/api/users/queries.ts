/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { queryOptions } from "@tanstack/react-query";
import type { QueryUsersRequestBody } from "@camunda/camunda-api-zod-schemas/8.10";
import { getApiBaseUrl } from "src/configuration/urlConfig";
import { unwrap } from "../request";
import { queryKeys } from "../queryKeys";
import { getUserDetails, searchUser } from ".";

type SearchUsersParams = QueryUsersRequestBody & { usernames?: string[] };

export const userQueries = {
  search: (params?: SearchUsersParams | Record<string, unknown>) =>
    queryOptions({
      queryKey: queryKeys.users.search(params),
      queryFn: () =>
        unwrap(searchUser(params as SearchUsersParams)(getApiBaseUrl())),
    }),
  detail: (username: string | undefined) =>
    queryOptions({
      queryKey: queryKeys.users.detail(username ?? ""),
      queryFn: () =>
        unwrap(
          getUserDetails({ username: username as string })(getApiBaseUrl()),
        ),
      enabled: !!username,
    }),
};
