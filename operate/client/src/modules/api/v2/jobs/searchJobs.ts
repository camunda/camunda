/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  GetJobsRequestBody,
  GetJobsResponseBody,
} from '@vzeta/camunda-api-zod-schemas/operate';
import {RequestResult, requestWithThrow} from 'modules/request';

const searchJobs = async (
  payload: GetJobsRequestBody,
): RequestResult<GetJobsResponseBody> => {
  return requestWithThrow<GetJobsResponseBody>({
    url: endpoints.getJobs.getUrl(),
    method: endpoints.getJobs.method,
    body: payload,
  });
};

export {searchJobs};
