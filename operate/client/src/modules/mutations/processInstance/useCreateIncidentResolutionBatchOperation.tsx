/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  useMutation,
  useQueryClient,
  type UseMutationOptions,
} from '@tanstack/react-query';
import {type CreateIncidentResolutionBatchOperationResponseBody} from '@vzeta/camunda-api-zod-schemas/8.8';
import {getBatchOperation} from 'modules/api/v2/batchOperations/getBatchOperation';
import {createIncidentResolutionBatchOperation} from 'modules/api/v2/processInstances/createIncidentResolutionBatchOperation';

const useCreateIncidentResolutionBatchOperation = (
  processInstanceKey: string,
  options?: Partial<
    UseMutationOptions<CreateIncidentResolutionBatchOperationResponseBody>
  >,
) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationKey: [
      'mutateBatchOperations',
      processInstanceKey,
      'incidentResolution',
    ],
    mutationFn: async () => {
      const {response, error} = await createIncidentResolutionBatchOperation({
        processInstanceKey,
      });

      if (response === null) {
        throw new Error(error.response?.statusText);
      }

      const {batchOperationKey} = response;

      await queryClient.fetchQuery({
        queryKey: ['queryBatchOperations', batchOperationKey],
        queryFn: async () => {
          const {response, error} = await getBatchOperation({
            batchOperationKey,
          });
          if (error) {
            throw new Error(error.response?.statusText);
          }

          if (response.state !== 'COMPLETED') {
            throw new Error('Batch operation is still pending');
          }

          return response;
        },
        retry: true,
        retryDelay: 5000,
      });

      return response;
    },
    ...options,
  });
};

export {useCreateIncidentResolutionBatchOperation};
