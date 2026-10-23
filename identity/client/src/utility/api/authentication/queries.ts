/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import { queryOptions } from "@tanstack/react-query";
import { getApiBaseUrl } from "src/configuration/urlConfig";
import { ApiError, unwrap } from "../request";
import { queryKeys } from "../queryKeys";
import { getAuthentication } from ".";

export const authenticationQueries = {
  me: () =>
    queryOptions({
      queryKey: queryKeys.authentication.me,
      queryFn: async () => {
        const currentUser = await unwrap(
          getAuthentication(undefined)(getApiBaseUrl()),
        );
        // An unprotected API answers a caller without a session with an anonymous user instead
        // of a 401, because no credentials can authenticate it there. Handle it like the 401.
        if ("anonymous" in currentUser && currentUser.anonymous === true) {
          throw new ApiError(401, null);
        }
        return currentUser;
      },
      meta: { skipErrorNotification: true },
    }),
};
