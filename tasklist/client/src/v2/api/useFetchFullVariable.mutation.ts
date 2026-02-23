/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {api} from 'v2/api';
import {useMutation, useQueryClient} from '@tanstack/react-query';
import {request} from 'common/api/request';
import type {
  Variable,
  QueryVariablesByUserTaskResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.9';
import {getAllVariablesQueryKey} from './useQueryAllVariables.query';

function useFetchFullVariable() {
  const client = useQueryClient();

  return useMutation({
    mutationFn: async (params: {variableKey: string; userTaskKey: string}) => {
      const {variableKey, userTaskKey} = params;
      const {response, error} = await request(api.getVariable(variableKey));

      if (response !== null) {
        const variable = (await response.json()) as Omit<
          Variable,
          'isTruncated'
        >;
        const variablesQueryKey = getAllVariablesQueryKey(userTaskKey);
        const allVariables =
          client.getQueryData<QueryVariablesByUserTaskResponseBody>(
            variablesQueryKey,
          );
        const hasVariable =
          allVariables?.items.some(
            (variable) => variable.variableKey === variableKey,
          ) ?? false;

        if (hasVariable) {
          client.setQueryData<QueryVariablesByUserTaskResponseBody>(
            variablesQueryKey,
            {
              ...allVariables!,
              items: allVariables!.items.map((oldVariable) =>
                oldVariable.variableKey === variableKey
                  ? {
                      ...variable,
                      value: variable.value,
                      isTruncated: false,
                    }
                  : oldVariable,
              ),
            },
          );
        }

        return variable;
      }

      throw error;
    },
  });
}

export {useFetchFullVariable};
