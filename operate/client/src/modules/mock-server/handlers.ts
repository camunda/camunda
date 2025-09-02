/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  type QueryJobsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {IS_LISTENERS_TAB_V2} from 'modules/feature-flags';
import {RequestHandler, http, HttpResponse} from 'msw';

const mockListenerEndpoint = http.post(
  endpoints.queryJobs.getUrl(),
  async () => {
    const mockResponse: QueryJobsResponseBody = {
      items: [
        {
          jobKey: '2251799813916032',
          type: 'task_start_el_1',
          worker: 'worker',
          state: 'CREATED',
          kind: 'EXECUTION_LISTENER',
          listenerEventType: 'START',
          retries: 3,
          isDenied: false,
          deniedReason: 'deniedReason',
          hasFailedWithRetriesLeft: false,
          errorCode: 'errorCode',
          errorMessage: 'errorMessage',
          deadline: 'deadline',
          endTime: '2024-05-27T07:42:43.705+0000',
          processDefinitionId: 'processDefinitionId',
          processDefinitionKey: 'processDefinitionKey',
          processInstanceKey: 'processInstanceKey',
          elementId: 'elementId',
          elementInstanceKey: 'elementInstanceKey',
          tenantId: 'tenantId',
        },
      ],
      page: {
        totalItems: 1,
        startCursor: 'startCursor',
        endCursor: 'endCursor',
      },
    };

    return HttpResponse.json(mockResponse);
  },
);

const handlers: RequestHandler[] = [
  ...(IS_LISTENERS_TAB_V2 ? [mockListenerEndpoint] : []),
];

export {handlers};
