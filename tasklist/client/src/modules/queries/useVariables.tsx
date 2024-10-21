/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery, type UseQueryOptions} from '@tanstack/react-query';
import {api} from 'modules/api';
import {request, type RequestError} from 'modules/request';
import type {Variable} from 'modules/types';
import type {UserTask} from '@vzeta/camunda-api-zod-schemas/tasklist';

type Params = {
  userTaskKey: UserTask['userTaskKey'];
  variableNames: Variable['name'][];
};

type Options = Pick<
  UseQueryOptions<Variable[], RequestError | Error>,
  'enabled' | 'refetchOnWindowFocus' | 'refetchOnReconnect'
>;

function useVariables(params: Params, options: Options = {}) {
  const {userTaskKey, variableNames} = params;
  const variablesQueryKey = ['variables', userTaskKey, ...variableNames];
  return useQuery<Variable[], RequestError | Error>({
    ...options,
    queryKey: variablesQueryKey,
    queryFn: async () => {
      const {response, error} = await request(
        api.searchVariables({userTaskKey, variableNames}),
      );

      if (response !== null) {
        return response.json();
      }

      throw error;
    },
  });
}

export {useVariables};
