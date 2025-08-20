/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, useQueryClient} from '@tanstack/react-query';
import {updateElementInstanceVariables} from 'modules/api/v2/elementInstances/updateElementInstanceVariables';
import type {ElementInstance} from '@vzeta/camunda-api-zod-schemas/8.8';
import {getQueryKey} from 'modules/queries/variables/useVariables';
import {searchVariables} from 'modules/api/v2/variables/searchVariables';

const getMutationKey = (elementInstanceKey: string) => [
  'updateElementInstanceVariables',
  elementInstanceKey,
];

function useElementInstanceVariables(
  elementInstanceKey: ElementInstance['elementInstanceKey'],
  processInstanceKey: ElementInstance['processInstanceKey'],
) {
  const queryClient = useQueryClient();
  const VARIABLE_NOT_FOUND = 'Variable not found';

  return useMutation({
    mutationKey: getMutationKey(elementInstanceKey),
    mutationFn: async (variable: {name: string; value: string}) => {
      const response = await updateElementInstanceVariables(
        elementInstanceKey,
        {
          variables: {[variable.name]: JSON.parse(variable.value)},
          local: true,
        },
      );

      if (!response.ok) {
        throw new Error(response.statusText);
      }

      await queryClient.fetchQuery({
        queryKey: [
          ...getQueryKey(processInstanceKey, elementInstanceKey),
          variable.name,
          variable.value,
        ],
        queryFn: async () => {
          const {response, error} = await searchVariables({
            filter: {
              name: variable.name,
              value: variable.value,
              processInstanceKey,
              scopeKey: elementInstanceKey,
            },
          });

          if (error) {
            throw new Error(error.response?.statusText);
          }

          if (response.items.length === 0) {
            throw new Error(VARIABLE_NOT_FOUND);
          }

          return response;
        },
        retry: (_, error) => {
          if (error.message === VARIABLE_NOT_FOUND) {
            return true;
          }
          return false;
        },
        retryDelay: 1000,
      });

      return;
    },
  });
}

export {useElementInstanceVariables, getMutationKey};
