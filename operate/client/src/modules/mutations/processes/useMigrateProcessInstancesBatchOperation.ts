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
import {migrateProcessInstancesBatchOperation} from 'modules/api/v2/processes/migrateProcessInstancesBatchOperation.ts';
import type {
  CreateMigrationBatchOperationRequestBody,
  CreateMigrationBatchOperationResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import type {RequestError} from 'modules/request';
import {queryKeys} from 'modules/queries/queryKeys';

const useMigrateProcessInstancesBatchOperation = (
  options?: Partial<
    UseMutationOptions<
      CreateMigrationBatchOperationResponseBody,
      RequestError,
      CreateMigrationBatchOperationRequestBody
    >
  >,
) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationKey: ['createProcessInstanceMigrationBatchOperation'],
    mutationFn: async (payload) => {
      const {response, error} =
        await migrateProcessInstancesBatchOperation(payload);
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

export {useMigrateProcessInstancesBatchOperation};
