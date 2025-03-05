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
            flowNodeId: 'messageCatchEvent',
            active: 2,
            canceled: 1,
            incidents: 3,
            completed: 4,
          },
          {
            flowNodeId: 'exclusiveGateway',
            active: 20,
            canceled: 10,
            incidents: 30,
            completed: 40,
          },
        ]),
      );
    },
  ),
];

const handlers: RequestHandler[] = [...mockStatisticsV2];

export {handlers};
