/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery, UseQueryResult} from '@tanstack/react-query';
import {
  QueryVariablesRequestBody,
  QueryVariablesResponseBody,
} from '@vzeta/camunda-api-zod-schemas/process-management';
import {RequestError} from 'modules/request';
import {searchVariables} from 'modules/api/v2/variables/searchVariables';

const VARIABLES_SEARCH_QUERY_KEY = 'variablesSearch';

function getQueryKey(payload: QueryVariablesRequestBody) {
  return [VARIABLES_SEARCH_QUERY_KEY, ...Object.values(payload)];
}

function useVariables<T = QueryVariablesResponseBody>(
  payload: QueryVariablesRequestBody,
  select?: (data: QueryVariablesResponseBody) => T,
): UseQueryResult<T, RequestError> {
  return useQuery({
    queryKey: getQueryKey(payload),
    queryFn: async () => {
      const {response, error} = await searchVariables(payload);

      if (response !== null) {
        return response;
      }

      throw error;
    },
    select,
  });
}

export {VARIABLES_SEARCH_QUERY_KEY, useVariables};
