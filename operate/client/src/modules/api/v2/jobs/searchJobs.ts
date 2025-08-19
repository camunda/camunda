/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type QueryJobsRequestBody,
  type QueryJobsResponseBody,
} from '@vzeta/camunda-api-zod-schemas/8.8';
import {requestWithThrow} from 'modules/request';

const searchJobs = async (payload: QueryJobsRequestBody) => {
  return requestWithThrow<QueryJobsResponseBody>({
    url: endpoints.queryJobs.getUrl(),
    method: endpoints.queryJobs.method,
    body: payload,
  });
};

export {searchJobs};
