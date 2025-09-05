/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type CreateModificationBatchOperationRequestBody,
  type CreateModificationBatchOperationResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {requestWithThrow} from 'modules/request';

const modifyProcessInstancesBatchOperation = async (
  payload: CreateModificationBatchOperationRequestBody,
) => {
  return requestWithThrow<CreateModificationBatchOperationResponseBody>({
    url: endpoints.createModificationBatchOperation.getUrl(),
    method: endpoints.createModificationBatchOperation.method,
    body: payload,
  });
};

export {modifyProcessInstancesBatchOperation};
