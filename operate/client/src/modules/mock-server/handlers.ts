/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  GetProcessDefinitionStatisticsRequestBody,
  GetProcessDefinitionStatisticsResponseBody,
} from '@vzeta/camunda-api-zod-schemas/operate';
import {RequestHandler, RestRequest, rest} from 'msw';

const mockStatisticsV2 = [
  rest.post(
    '/v2/process-definitions/:processDefinitionKey/statistics/flownode-instances',
    async (
      req: RestRequest<GetProcessDefinitionStatisticsRequestBody>,
      res,
      ctx,
    ) => {
      const mockResponse: GetProcessDefinitionStatisticsResponseBody = {
        items: [
          {
            flowNodeId: 'Gateway_15jzrqe',
            active: 0,
            canceled: 0,
            incidents: 20,
            completed: 0,
          },
          {
            flowNodeId: 'exclusiveGateway',
            active: 0,
            canceled: 0,
            incidents: 20,
            completed: 0,
          },
          {
            flowNodeId: 'alwaysFailingTask',
            active: 20,
            canceled: 0,
            incidents: 0,
            completed: 0,
          },
          {
            flowNodeId: 'messageCatchEvent',
            active: 0,
            canceled: 0,
            incidents: 20,
            completed: 0,
          },
          {
            flowNodeId: 'upperTask',
            active: 20,
            canceled: 0,
            incidents: 0,
            completed: 0,
          },
        ],
      };

      return res(ctx.json(mockResponse));
    },
  ),
];

const handlers: RequestHandler[] = [...mockStatisticsV2];

export {handlers};
