/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {updateElementInstanceVariables} from 'modules/api/v2/elementInstances/updateElementInstanceVariables';
import type {ElementInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {searchVariables} from 'modules/api/v2/variables/searchVariables';
import {getVariable} from 'modules/api/v2/variables/getVariable';
import {queryKeys} from 'modules/queries/queryKeys';

function useElementInstanceVariables(
  elementInstanceKey: ElementInstance['elementInstanceKey'],
  processInstanceKey: ElementInstance['processInstanceKey'],
) {
  const queryClient = useQueryClient();

<<<<<<< HEAD
  return useMutation({
    mutationFn: async (variable: {name: string; value: string}) => {
      const {error} = await updateElementInstanceVariables(elementInstanceKey, {
        variables: {[variable.name]: JSON.parse(variable.value)},
        local: true,
      });
=======
  return useMutation<
    void,
    {status: number; statusText: string},
    {name: string; value: string; variableKey?: string}
  >({
    mutationFn: async (variable) => {
      const response = await updateElementInstanceVariables(
        elementInstanceKey,
        {
          variables: {[variable.name]: JSON.parse(variable.value)},
          local: true,
        },
      );
>>>>>>> dbca852b (fix: truncated variables create/update stuck in loading state)

      if (error !== null) {
        throw new Error(error.response?.statusText);
      }

      if (variable.variableKey !== undefined) {
        await queryClient.fetchQuery({
          queryKey: queryKeys.variable.get(variable.variableKey),
          queryFn: async () => {
            const {response, error} = await getVariable(variable.variableKey!);

            if (error) {
              throw new Error(error.response?.statusText);
            }

            if (response.value !== variable.value) {
              throw new Error('Variable not updated yet');
            }

            return response;
          },
          retry: true,
          retryDelay: 1000,
        });
      } else {
        await queryClient.fetchQuery({
          queryKey: queryKeys.variables.searchWithFilter({
            processInstanceKey,
            scopeKey: elementInstanceKey,
            name: variable.name,
          }),
          queryFn: async () => {
            const {response, error} = await searchVariables({
              filter: {
                name: variable.name,
                processInstanceKey,
                scopeKey: elementInstanceKey,
              },
            });

            if (error) {
              throw new Error(error.response?.statusText);
            }

            if (response.items.length === 0) {
              throw new Error('Variable not found');
            }

            return response;
          },
          retry: true,
          retryDelay: 1000,
        });
      }

      return;
    },
  });
}

export {useElementInstanceVariables};
