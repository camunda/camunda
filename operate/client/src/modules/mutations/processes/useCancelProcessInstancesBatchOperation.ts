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
} from '@camunda/camunda-api-zod-schemas/8.8';
import {createCancellationBatchOperation} from 'modules/api/v2/processInstances/createCancellationBatchOperation';
import {BATCH_OPERATIONS_QUERY_KEY} from 'modules/queries/batch-operations/useBatchOperations';

const useCancelProcessInstancesBatchOperation = (
  options?: Partial<
    UseMutationOptions<
      CreateCancellationBatchOperationResponseBody,
      Error,
      CreateCancellationBatchOperationRequestBody
    >
  >,
) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationKey: ['createProcessInstanceCancellationBatchOperation'],
    mutationFn: async (payload) => {
      const {response, error} = await createCancellationBatchOperation(payload);
      if (response !== null) {
        await queryClient.invalidateQueries({
          queryKey: [BATCH_OPERATIONS_QUERY_KEY],
        });
        return response;
      }
      throw error;
    },
    ...options,
  });
};

export {useCancelProcessInstancesBatchOperation};
