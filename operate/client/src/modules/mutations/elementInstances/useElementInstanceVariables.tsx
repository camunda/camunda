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
import {queryKeys} from 'modules/queries/queryKeys';

function useElementInstanceVariables(
  elementInstanceKey: ElementInstance['elementInstanceKey'],
  processInstanceKey: ElementInstance['processInstanceKey'],
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (variable: {name: string; value: string}) => {
      const {error} = await updateElementInstanceVariables(elementInstanceKey, {
        variables: {[variable.name]: JSON.parse(variable.value)},
        local: true,
      });

      if (error !== null) {
        throw new Error(error.response?.statusText);
      }

      await queryClient.fetchQuery({
        queryKey: queryKeys.variables.searchWithFilter({
          processInstanceKey,
          scopeKey: elementInstanceKey,
          name: variable.name,
          value: variable.value,
        }),
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
            throw new Error('Variable not found');
          }

          return response;
        },
        retry: true,
        retryDelay: 1000,
      });

      return;
    },
  });
}

export {useElementInstanceVariables};
