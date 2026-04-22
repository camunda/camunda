/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  type CreateDecisionInstancesDeletionBatchOperationRequestBody,
  type CreateDecisionInstancesDeletionBatchOperationResponseBody,
  endpoints,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {requestWithThrow} from 'modules/request';

const createDeletionBatchOperation = async (
  payload: CreateDecisionInstancesDeletionBatchOperationRequestBody,
) => {
  return requestWithThrow<CreateDecisionInstancesDeletionBatchOperationResponseBody>(
    {
      url: endpoints.createDecisionInstancesDeletionBatchOperation.getUrl(),
      method: endpoints.createDecisionInstancesDeletionBatchOperation.method,
      body: payload,
    },
  );
};

export {createDeletionBatchOperation};
