/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type QueryUserTasksResponseBody,
  type QueryUserTasksRequestBody,
} from '@vzeta/camunda-api-zod-schemas';
import {requestWithThrow, type RequestResult} from 'modules/request';

const searchUserTasks = async (
  payload: QueryUserTasksRequestBody,
): RequestResult<QueryUserTasksResponseBody> => {
  return requestWithThrow<QueryUserTasksResponseBody>({
    url: endpoints.queryUserTasks.getUrl(),
    method: endpoints.queryUserTasks.method,
    body: payload,
  });
};

export {searchUserTasks};
