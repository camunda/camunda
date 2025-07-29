/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type QueryProcessInstancesRequestBody,
  type QueryProcessInstancesResponseBody,
} from '@vzeta/camunda-api-zod-schemas/8.8';
import {type RequestResult, requestWithThrow} from 'modules/request';

const searchProcessInstances = async (
  payload: QueryProcessInstancesRequestBody,
): RequestResult<QueryProcessInstancesResponseBody> => {
  return requestWithThrow<QueryProcessInstancesResponseBody>({
    url: endpoints.queryProcessInstances.getUrl(),
    method: endpoints.queryProcessInstances.method,
    body: payload,
  });
};

export {searchProcessInstances};
