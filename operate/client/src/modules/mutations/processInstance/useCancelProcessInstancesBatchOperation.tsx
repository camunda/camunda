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
  CreateCancellationBatchOperationRequestBody,
  CreateCancellationBatchOperationResponseBody,
} from '@vzeta/camunda-api-zod-schemas/8.8';
import {cancelProcessInstancesBatchOperation} from 'modules/api/v2/processInstances/cancelProcessInstancesBatchOperation';
import {queryBatchOperations} from 'modules/api/v2/batchOperations/queryBatchOperations';

function useCancelProcessInstancesBatchOperation(
  options?: Partial<
    UseMutationOptions<
      CreateCancellationBatchOperationRequestBody,
      Error,
      CreateCancellationBatchOperationResponseBody
    >
  >,
) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationKey: ['createProcessInstanceCancellationBatchOperation'],
    mutationFn: async (
      payload: CreateCancellationBatchOperationRequestBody,
    ) => {
      const {response, error} =
        await cancelProcessInstancesBatchOperation(payload);

      if (response !== null) {
        const queryPayload = {
          filter: {batchOperationKey: response.batchOperationKey},
        };
        const OPERATION_PENDING = 'Batch operation is still pending';

        await queryClient.fetchQuery({
          queryKey: ['queryBatchOperations', queryPayload],
          queryFn: async () => {
            const {response: batchResponse, error} =
              await queryBatchOperations(queryPayload);
            if (error) {
              throw new Error(error.response?.statusText);
            }

            const hasActiveOperation = batchResponse.items.some(
              (item) => item.state === 'ACTIVE',
            );

            if (batchResponse.page.totalItems === 0 || hasActiveOperation) {
              throw new Error(OPERATION_PENDING);
            }
            return batchResponse;
          },
          retry: (_, error) => {
            return error.message === OPERATION_PENDING;
          },
          retryDelay: 5000,
        });

        return response;
      }
      throw error;
    },
    ...options,
  });
}

export {useCancelProcessInstancesBatchOperation};
