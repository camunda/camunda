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
import {
  type CreateIncidentResolutionBatchOperationResponseBody,
  type QueryBatchOperationsRequestBody,
} from '@vzeta/camunda-api-zod-schemas/8.8';
import {queryBatchOperations} from 'modules/api/v2/batchOperations/queryBatchOperations';
import {createIncidentResolutionBatchOperation} from 'modules/api/v2/processInstances/createIncidentResolutionBatchOperation';

const getMutationKey = (processInstanceKey: string) => {
  return ['mutateBatchOperations', processInstanceKey, 'incidentResolution'];
};

function getQueryKey(payload: QueryBatchOperationsRequestBody) {
  return ['queryBatchOperations', ...Object.values(payload)];
}

const useCreateIncidentResolutionBatchOperation = (
  processInstanceKey: string,
  options?: Partial<
    UseMutationOptions<CreateIncidentResolutionBatchOperationResponseBody>
  >,
) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationKey: getMutationKey(processInstanceKey),
    mutationFn: async () => {
      const {response, error} = await createIncidentResolutionBatchOperation({
        processInstanceKey,
      });
      if (error) {
        throw new Error(error.response?.statusText);
      }

      const payload = {filter: {batchOperationKey: response.batchOperationKey}};
      const OPERATION_PENDING = 'Batch operation is still pending';

      await queryClient.fetchQuery({
        queryKey: getQueryKey(payload),
        queryFn: async () => {
          const {response, error} = await queryBatchOperations(payload);
          if (error) {
            throw new Error(error.response?.statusText);
          }

          const hasActiveOperation = response.items.some(
            (item) => item.state === 'ACTIVE',
          );

          if (response.page.totalItems === 0 || hasActiveOperation) {
            throw new Error(OPERATION_PENDING);
          }
          return response;
        },
        retry: (_, error) => {
          if (error.message === OPERATION_PENDING) {
            return true;
          }
          return false;
        },
        retryDelay: 5000,
      });

      return response;
    },
    ...options,
  });
};

export {useCreateIncidentResolutionBatchOperation, getMutationKey};
