/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints as tasklistEndpoints,
  type QueryUserTasksRequestBody,
} from '@vzeta/camunda-api-zod-schemas/tasklist';
import {
  endpoints as operateEndpoints,
  type QueryProcessDefinitionsRequestBody,
} from '@vzeta/camunda-api-zod-schemas/operate';
import {BASE_REQUEST_OPTIONS, getFullURL} from 'common/api';

const api = {
  queryTasks: (body: QueryUserTasksRequestBody = {}) => {
    return new Request(getFullURL(tasklistEndpoints.queryUserTasks.getUrl()), {
      ...BASE_REQUEST_OPTIONS,
      method: tasklistEndpoints.queryUserTasks.method,
      body: Object.keys(body).length > 0 ? JSON.stringify(body) : undefined,
      headers: {
        'Content-Type': 'application/json',
        'x-is-polling': 'true',
      },
    });
  },
  queryProcesses: (body: QueryProcessDefinitionsRequestBody = {}) => {
    return new Request(
      getFullURL(operateEndpoints.queryProcessDefinitions.getUrl()),
      {
        ...BASE_REQUEST_OPTIONS,
        method: operateEndpoints.queryProcessDefinitions.method,
        body: Object.keys(body).length > 0 ? JSON.stringify(body) : undefined,
        headers: {
          'Content-Type': 'application/json',
        },
      },
    );
  },
} as const;

export {api};
