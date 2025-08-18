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
} from '@vzeta/camunda-api-zod-schemas/8.8';
import {type RequestResult, requestWithThrow} from 'modules/request';

const modifyProcessInstancesBatchOperation = async (
  payload: CreateModificationBatchOperationRequestBody,
): RequestResult<CreateModificationBatchOperationResponseBody> => {
  return requestWithThrow<CreateModificationBatchOperationResponseBody>({
    url: endpoints.createModificationBatchOperation.getUrl(),
    method: endpoints.createModificationBatchOperation.method,
    body: payload,
  });
};

export {modifyProcessInstancesBatchOperation};
