/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useQuery, UseQueryOptions} from '@tanstack/react-query';
import {api} from 'modules/api';
import {request, RequestError} from 'modules/request';
import {Task, Variable} from 'modules/types';

type Params = {
  taskId: Task['id'];
  variableNames: Variable['name'][];
};

type Options = Pick<
  UseQueryOptions<Variable[], RequestError | Error>,
  'enabled' | 'refetchOnWindowFocus' | 'refetchOnReconnect'
>;

function useVariables(params: Params, options: Options = {}) {
  const {taskId, variableNames} = params;
  const variablesQueryKey = ['variables', taskId, ...variableNames];
  return useQuery<Variable[], RequestError | Error>({
    ...options,
    queryKey: variablesQueryKey,
    queryFn: async () => {
      const {response, error} = await request(
        api.searchVariables({taskId, variableNames}),
      );

      if (response !== null) {
        return response.json();
      }

      throw error;
    },
  });
}

export {useVariables};
