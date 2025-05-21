/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {GetJobsResponseBody} from '@vzeta/camunda-api-zod-schemas/operate';
import {RequestHandler, rest} from 'msw';

const mockEndpoints = [
  rest.post('/v2/jobs/search', async (req, res, ctx) => {
    const mockResponse: GetJobsResponseBody = {
      items: [
        {
          jobKey: '2251799813916032',
          type: 'type',
          worker: 'worker',
          state: 'CREATED',
          kind: 'EXECUTION_LISTENER',
          listenerEventType: 'UNSPECIFIED',
          retries: 3,
          isDenied: false,
          deniedReason: 'deniedReason',
          hasFailedWithRetriesLeft: false,
          errorCode: 'errorCode',
          errorMessage: 'errorMessage',
          deadline: 'deadline',
          endTime: 'endTime',
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
        firstSortValues: [0, 1],
        lastSortValues: [0, 1],
      },
    };

    return res(ctx.json(mockResponse));
  }),
];

const handlers: RequestHandler[] = [...mockEndpoints];

export {handlers};
