/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {mockPostRequest} from '../../mockRequest';
import {
  GetJobsResponseBody,
  endpoints,
} from '@vzeta/camunda-api-zod-schemas/operate';

const mockSearchJobs = (contextPath = '') =>
  mockPostRequest<GetJobsResponseBody>(
    contextPath + endpoints.getJobs.getUrl(),
  );

export {mockSearchJobs};
