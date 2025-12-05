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
import {modifyProcessInstancesBatchOperation} from 'modules/api/v2/processInstances/modifyProcessInstancesBatchOperation';
import type {
  CreateModificationBatchOperationRequestBody,
  CreateModificationBatchOperationResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import type {RequestError} from 'modules/request';
import {queryKeys} from 'modules/queries/queryKeys';

const useModifyProcessInstancesBatchOperation = (
  options?: Partial<
    UseMutationOptions<
      CreateModificationBatchOperationResponseBody,
      RequestError,
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

export {useModifyProcessInstancesBatchOperation};
