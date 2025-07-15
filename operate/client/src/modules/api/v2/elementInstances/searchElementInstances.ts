/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type QueryElementInstancesRequestBody,
  type QueryElementInstancesResponseBody,
} from '@vzeta/camunda-api-zod-schemas/8.8';
import {requestWithThrow, type RequestResult} from 'modules/request';

const searchElementInstances = async (
  payload: QueryElementInstancesRequestBody,
): RequestResult<QueryElementInstancesResponseBody> => {
  return requestWithThrow<QueryElementInstancesResponseBody>({
    url: endpoints.queryElementInstances.getUrl(),
    method: endpoints.queryElementInstances.method,
    body: payload,
  });
};

export {searchElementInstances};
