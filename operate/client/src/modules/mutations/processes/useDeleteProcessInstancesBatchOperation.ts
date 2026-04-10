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
  CreateDeletionBatchOperationRequestBody,
  CreateDeletionBatchOperationResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {createDeletionBatchOperation} from 'modules/api/v2/processInstances/createDeletionBatchOperation';
import type {RequestError} from 'modules/request';
import {queryKeys} from 'modules/queries/queryKeys';

const useDeleteProcessInstancesBatchOperation = (
  options?: Partial<
    UseMutationOptions<
      CreateDeletionBatchOperationResponseBody,
      RequestError,
      CreateDeletionBatchOperationRequestBody
    >
  >,
) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: async (payload) => {
      const {response, error} = await createDeletionBatchOperation(payload);
      if (response !== null) {
        await queryClient.invalidateQueries({
          queryKey: queryKeys.batchOperations.query(),
        });
        return response;
      }
      throw error;
    },
    ...options,
  });
};

export {useDeleteProcessInstancesBatchOperation};
