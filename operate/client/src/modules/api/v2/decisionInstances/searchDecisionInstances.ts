/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type QueryDecisionInstancesRequestBody,
  type QueryDecisionInstancesResponseBody,
} from '@vzeta/camunda-api-zod-schemas';
import {requestWithThrow, type RequestResult} from 'modules/request';

const searchDecisionInstances = async (
  payload: QueryDecisionInstancesRequestBody,
): RequestResult<QueryDecisionInstancesResponseBody> => {
  return requestWithThrow<QueryDecisionInstancesResponseBody>({
    url: endpoints.queryDecisionInstances.getUrl(),
    method: endpoints.queryDecisionInstances.method,
    body: payload,
  });
};

export {searchDecisionInstances};
