/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {RequestHandler, rest} from 'msw';
import {mockListenerInstancesShort} from 'modules/mocks/mockListenerInstances';
import {ListenerPayload} from 'modules/api/processInstances/fetchProcessInstanceListeners';

const listenersHandler = [
  rest.post(
    '/api/process-instances/:instanceId/listeners',
    async (req, res, ctx) => {
      const body: ListenerPayload = await req.json();

      if (body.flowNodeId) {
        return res(
          ctx.json({totalCount: 1, listeners: [mockListenerInstancesShort[0]]}),
        );
      } else if (body.flowNodeInstanceId) {
        return res(
          ctx.json({totalCount: 1, listeners: [mockListenerInstancesShort[1]]}),
        );
      }
      return res(ctx.json([]));
    },
  ),
];

const handlers: RequestHandler[] = [...listenersHandler];

export {handlers};
