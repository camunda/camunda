/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  QueryKey,
  useQuery,
  useQueryClient,
  UseQueryOptions,
} from '@tanstack/react-query';
import {api} from 'modules/api';
import {request, RequestError} from 'modules/request';
import {FullVariable, Task, Variable} from 'modules/types';
import {useState} from 'react';

type Param = {
  taskId: Task['id'];
};

type Options = Pick<
  UseQueryOptions<Variable[], RequestError | Error>,
  'enabled' | 'refetchOnWindowFocus' | 'refetchOnReconnect'
>;

function useFetchFullVariable(variablesQueryKey: QueryKey) {
  const client = useQueryClient();
  const [requestedVariables, setRequestedVariables] = useState<
    Variable['id'][]
  >([]);

  async function fetch(id: Variable['id']) {
    setRequestedVariables((variables) => [...variables, id]);
    const fullVariable = await client.fetchQuery<
      Pick<FullVariable, 'id' | 'name' | 'value'>,
      RequestError | Error
    >({
      queryKey: ['variable', id],
      queryFn: async () => {
        const {response, error} = await request(api.getFullVariable(id));

        if (response !== null) {
          return response.json();
        }

        throw error;
      },
    });

    setRequestedVariables((variables) =>
      variables.filter((variable) => variable !== id),
    );

    client.setQueryData<Variable[]>(variablesQueryKey, (cachedVariables) =>
      cachedVariables?.map((variable) => {
        if (variable.id === id) {
          return {
            ...variable,
            ...fullVariable,
            isValueTruncated: false,
          } satisfies FullVariable;
        }

        return variable;
      }),
    );
  }

  return {
    fetch,
    variablesLoading: requestedVariables,
  };
}

function useAllVariables(params: Param, options: Options = {}) {
  const {taskId} = params;
  const variablesQueryKey = ['variables', taskId];
  const queryResult = useQuery<Variable[], RequestError | Error>({
    ...options,
    queryKey: variablesQueryKey,
    queryFn: async () => {
      const {response, error} = await request(api.getAllVariables({taskId}));

      if (response !== null) {
        return response.json();
      }

      throw error;
    },
  });
  const {
    fetch: fetchFullVariable,
    variablesLoading: variablesLoadingFullValue,
  } = useFetchFullVariable(variablesQueryKey);

  return {
    ...queryResult,
    fetchFullVariable,
    variablesLoadingFullValue,
  };
}

export {useAllVariables};
