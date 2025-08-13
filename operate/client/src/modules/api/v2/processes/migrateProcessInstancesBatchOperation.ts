/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type RequestResult, requestWithThrow} from 'modules/request';
import {
  endpoints,
  type CreateMigrationBatchOperationRequestBody,
  type CreateMigrationBatchOperationResponseBody,
} from '@vzeta/camunda-api-zod-schemas/8.8';

const migrateProcessInstancesBatchOperation = async (
  payload: CreateMigrationBatchOperationRequestBody,
): RequestResult<CreateMigrationBatchOperationResponseBody> => {
  return requestWithThrow<CreateMigrationBatchOperationResponseBody>({
    url: endpoints.createMigrationBatchOperation.getUrl(),
    method: endpoints.createMigrationBatchOperation.method,
    body: payload,
  });
};

export {migrateProcessInstancesBatchOperation};
