/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {skipToken, UseQueryOptions} from '@tanstack/react-query';
import {RequestError, RequestResult} from 'modules/request';

function genericQueryOptions<T, TSelect = T>(
  queryKey: unknown[],
  fetchFunction: () => RequestResult<T>,
  options?: UseQueryOptions<T, RequestError, TSelect>,
  skip?: boolean,
): UseQueryOptions<T, RequestError, TSelect> {
  return {
    queryKey,
    queryFn: skip
      ? skipToken
      : async () => {
          const {response, error} = await fetchFunction();

          if (response !== null) {
            return response;
          }

          throw error;
        },
    ...options,
  };
}

export {genericQueryOptions};
