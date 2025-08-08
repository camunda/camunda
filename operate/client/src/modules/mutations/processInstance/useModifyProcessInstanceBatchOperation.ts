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
import {queryBatchOperations} from 'modules/api/v2/batchOperations/queryBatchOperations';
import {modifyProcessInstancesBatchOperation} from 'modules/api/v2/processInstances/modifyProcessInstancesBatchOperation';
import type {
  CreateModificationBatchOperationRequestBody,
  CreateModificationBatchOperationResponseBody,
} from '@vzeta/camunda-api-zod-schemas/8.8';

const useModifyProcessInstanceBatchOperation = (
  options?: Partial<
    UseMutationOptions<
      CreateModificationBatchOperationResponseBody,
      Error,
      CreateModificationBatchOperationRequestBody
    >
  >,
) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationKey: ['createProcessInstanceModificationBatchOperation'],
    mutationFn: async (
      payload: CreateModificationBatchOperationRequestBody,
    ) => {
      const {response, error} =
        await modifyProcessInstancesBatchOperation(payload);

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
};

export {useModifyProcessInstanceBatchOperation};
