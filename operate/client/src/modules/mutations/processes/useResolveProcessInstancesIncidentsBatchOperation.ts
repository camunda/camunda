/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  useMutation,
  type UseMutationOptions,
  useQueryClient,
} from '@tanstack/react-query';
import type {
  CreateIncidentResolutionBatchOperationRequestBody,
  CreateIncidentResolutionBatchOperationResponseBody,
} from '@vzeta/camunda-api-zod-schemas/8.8';
import {resolveProcessInstancesIncidentsBatchOperation} from 'modules/api/v2/processes/resolveProcessInstancesIncidentsBatchOperation';

const useResolveProcessInstancesIncidentsBatchOperation = (
  options?: Partial<
    UseMutationOptions<
      CreateIncidentResolutionBatchOperationResponseBody,
      Error,
      CreateIncidentResolutionBatchOperationRequestBody
    >
  >,
) => {
  const queryClient = useQueryClient();
  return useMutation({
    mutationKey: ['createProcessInstanceIncidentResolutionBatchOperation'],
    mutationFn: async (payload) => {
      const {response, error} =
        await resolveProcessInstancesIncidentsBatchOperation(payload);
      if (response !== null) {
        await queryClient.invalidateQueries({
          queryKey: ['queryBatchOperations'],
        });
        return response;
      }
      throw error;
    },
    ...options,
  });
};

export {useResolveProcessInstancesIncidentsBatchOperation};
