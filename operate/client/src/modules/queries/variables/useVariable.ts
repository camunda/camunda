/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {skipToken, useQuery, type UseQueryResult} from '@tanstack/react-query';
import type {Variable} from '@vzeta/camunda-api-zod-schemas';
import type {RequestError} from 'modules/request';
import {getVariable} from 'modules/api/v2/variables/getVariable';

const VARIABLES_GET_QUERY_KEY = 'variablesGet';

function getQueryKey(variableKey?: string) {
  return [VARIABLES_GET_QUERY_KEY, variableKey];
}

function useVariable<T = Variable>({
  isPreview = true,
  variableKey,
  select,
}: {
  isPreview?: boolean;
  variableKey?: string;
  select?: (data: Variable) => T;
}): UseQueryResult<T, RequestError> {
  return useQuery({
    queryKey: getQueryKey(variableKey),
    queryFn:
      !!variableKey && isPreview
        ? async () => {
            const {response, error} = await getVariable(variableKey);

            if (response !== null) {
              return response;
            }

            throw error;
          }
        : skipToken,
    select,
  });
}

export {VARIABLES_GET_QUERY_KEY, useVariable};
