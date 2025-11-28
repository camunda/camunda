/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ResolveProcessInstanceIncidentsResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {
  useMutation,
  useQueryClient,
  type UseMutationOptions,
} from '@tanstack/react-query';
import {getBatchOperation} from 'modules/api/v2/batchOperations/getBatchOperation';
import {resolveProcessInstanceIncidents} from 'modules/api/v2/processInstances/resolveProcessInstanceIncidents';
import {queryKeys} from 'modules/queries/queryKeys';

type Options = Omit<
  UseMutationOptions<
    ResolveProcessInstanceIncidentsResponseBody,
    {status?: number; statusText?: string},
    void,
    unknown
  >,
  'mutationFn'
>;

function useResolveProcessInstanceIncidents(
  processInstanceKey: string,
  options?: Options,
) {
  const queryClient = useQueryClient();

  return useMutation({
    ...options,
    mutationFn: async () => {
      const {response, error} =
        await resolveProcessInstanceIncidents(processInstanceKey);
      if (response === null) {
        throw {
          status: error.response?.status,
          statusText: error.response?.statusText,
        };
      }

      await queryClient.fetchQuery({
        queryKey: queryKeys.batchOperations.get(response.batchOperationKey),
        queryFn: async () => {
          const {response: batchOperation, error} = await getBatchOperation({
            batchOperationKey: response.batchOperationKey,
          });

          if (error !== null) {
            throw error;
          }

          if (
            batchOperation.state === 'ACTIVE' ||
            batchOperation.state === 'CREATED'
          ) {
            throw new Error('batch operation is still active');
          }

          return batchOperation;
        },
        retry: true,
      });

      return response;
    },
  });
}

export {useResolveProcessInstanceIncidents};
