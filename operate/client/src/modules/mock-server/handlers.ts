/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ListenerPayload} from 'modules/api/processInstances/fetchProcessInstanceListeners';
import {mockListenerInstances} from 'modules/mocks/mockListenerInstances';
import {RequestHandler, rest} from 'msw';

const listenersHandler = [
  rest.post(
    '/api/process-instances/:instanceId/listeners',
    async (req, res, ctx) => {
      const body: ListenerPayload = await req.json();

      if (body.listenerTypeFilter === 'EXECUTION_LISTENER') {
        return res(
          ctx.json({
            totalCount: 16,
            listeners: mockListenerInstances
              .filter((l) => l.listenerType === 'EXECUTION_LISTENER')
              .slice(0, 2),
          }),
        );
      } else if (body.listenerTypeFilter === 'USER_TASK_LISTENER') {
        return res(
          ctx.json({
            totalCount: 2,
            listeners: mockListenerInstances.filter(
              (l) => l.listenerType === 'USER_TASK_LISTENER',
            ),
          }),
        );
      }

      return res(
        ctx.json({
          totalCount: 2,
          listeners: mockListenerInstances.slice(0, 4),
        }),
      );
    },
  ),
];

const handlers: RequestHandler[] = [...listenersHandler];

export {handlers};
