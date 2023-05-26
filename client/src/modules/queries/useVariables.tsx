/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useQuery, useQueryClient, UseQueryOptions} from '@tanstack/react-query';
import {api} from 'modules/api';
import {request, RequestError} from 'modules/request';
import {Task, Variable} from 'modules/types';
import {useState} from 'react';

type Params = {
  taskId: Task['id'];
  variableNames?: Variable['name'][];
};

function useVariables(
  params: Params,
  options: Pick<
    UseQueryOptions<Variable[], RequestError | Error>,
    'enabled' | 'refetchOnWindowFocus' | 'refetchOnReconnect'
  > = {},
) {
  const client = useQueryClient();
  const [variablesLoadingFullValue, setVariablesLoadingFullValue] = useState<
    Variable['id'][]
  >([]);
  const {taskId, variableNames = []} = params;
  const variablesQueryKey = ['variables', taskId, ...variableNames];
  const queryResult = useQuery<Variable[], RequestError | Error>({
    ...options,
    queryKey: variablesQueryKey,
    queryFn: async () => {
      const {response, error} = await request(
        api.searchVariables({taskId, variableNames}),
      );

      if (response !== null) {
        return response.json();
      }

      throw error ?? new Error('Could not fetch variables');
    },
  });

  async function fetchFullVariable(id: Variable['id']) {
    setVariablesLoadingFullValue((variables) => [...variables, id]);
    const fullVariable = await client.fetchQuery<
      Variable,
      RequestError | Error
    >({
      queryKey: ['variable', id],
      queryFn: async () => {
        const {response, error} = await request(api.getFullVariable(id));

        if (response !== null) {
          return response.json();
        }

        throw error ?? new Error('Could not fetch variable');
      },
    });

    setVariablesLoadingFullValue((variables) =>
      variables.filter((variable) => variable !== id),
    );

    client.setQueryData<Variable[]>(variablesQueryKey, (cachedVariables) =>
      cachedVariables?.map((variable) => {
        if (variable.id === id) {
          return {
            ...fullVariable,
            isValueTruncated: false,
          };
        }

        return variable;
      }),
    );
  }

  return {
    ...queryResult,
    fetchFullVariable,
    variablesLoadingFullValue,
  };
}

export {useVariables};
