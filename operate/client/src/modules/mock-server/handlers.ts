/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {RequestHandler, RestRequest, rest} from 'msw';

const mockStatisticsV2 = [
  rest.post(
    '/v2/process-instances/statistics',
    async (req: RestRequest<any>, res, ctx) => {
      return res(
        ctx.json([
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
        ]),
      );
    },
  ),
];

const handlers: RequestHandler[] = [...mockStatisticsV2];

export {handlers};
