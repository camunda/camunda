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
import {migrateProcessInstanceBatchOperation} from 'modules/api/v2/processInstances/migrateProcessInstanceBatchOperation';
import type {
  CreateMigrationBatchOperationRequestBody,
  CreateMigrationBatchOperationResponseBody,
} from '@vzeta/camunda-api-zod-schemas/8.8';

const useMigrateProcessInstanceBatchOperation = (
  options?: Partial<
    UseMutationOptions<
      CreateMigrationBatchOperationResponseBody,
      Error,
      CreateMigrationBatchOperationRequestBody
    >
  >,
) => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationKey: ['createProcessInstanceMigrationBatchOperation'],
    mutationFn: async (payload) => {
      const {response, error} =
        await migrateProcessInstanceBatchOperation(payload);
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

export {useMigrateProcessInstanceBatchOperation};
