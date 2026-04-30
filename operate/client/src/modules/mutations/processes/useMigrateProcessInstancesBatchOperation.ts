/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMutation, type UseMutationOptions} from '@tanstack/react-query';
import {migrateProcessInstancesBatchOperation} from 'modules/api/v2/processes/migrateProcessInstancesBatchOperation';
import type {
  CreateMigrationBatchOperationRequestBody,
  CreateMigrationBatchOperationResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import type {RequestError} from 'modules/request';

const useMigrateProcessInstancesBatchOperation = (
  options?: Partial<
    UseMutationOptions<
      CreateMigrationBatchOperationResponseBody,
      RequestError,
      CreateMigrationBatchOperationRequestBody
    >
  >,
) => {
  return useMutation({
    mutationKey: ['createProcessInstanceMigrationBatchOperation'],
    mutationFn: async (payload) => {
      const {response, error} =
        await migrateProcessInstancesBatchOperation(payload);
      if (response !== null) {
        return response;
      }
      throw error;
    },
    ...options,
  });
};

export {useMigrateProcessInstancesBatchOperation};
