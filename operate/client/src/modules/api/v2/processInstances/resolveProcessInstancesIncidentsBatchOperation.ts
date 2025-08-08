/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  type CreateIncidentResolutionBatchOperationRequestBody,
  type CreateIncidentResolutionBatchOperationResponseBody,
  endpoints,
} from '@vzeta/camunda-api-zod-schemas/8.8';
import {requestWithThrow, type RequestResult} from 'modules/request';

const resolveProcessInstancesIncidentsBatchOperation = async (
  payload: CreateIncidentResolutionBatchOperationRequestBody,
): RequestResult<CreateIncidentResolutionBatchOperationResponseBody> => {
  return requestWithThrow({
    url: endpoints.createIncidentResolutionBatchOperation.getUrl(),
    method: endpoints.createIncidentResolutionBatchOperation.method,
    body: payload,
  });
};

export {resolveProcessInstancesIncidentsBatchOperation};
