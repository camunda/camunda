/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type QueryUserTasksRequestBody,
} from '@vzeta/camunda-api-zod-schemas/tasklist';
import {BASE_REQUEST_OPTIONS, getFullURL} from 'common/api';

const api = {
  queryTasks: (body: QueryUserTasksRequestBody) => {
    return new Request(getFullURL(endpoints.queryUserTasks.getUrl()), {
      ...BASE_REQUEST_OPTIONS,
      method: endpoints.queryUserTasks.method,
      body: JSON.stringify(body),
      headers: {
        'Content-Type': 'application/json',
        'x-is-polling': 'true',
      },
    });
  },
} as const;

export {api};
