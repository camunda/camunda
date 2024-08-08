/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IS_LISTENERS_TAB_SUPPORTED} from 'modules/feature-flags';
import {mockListeners} from 'modules/mocks/mockListeners';
import {RequestHandler, rest} from 'msw';

const listenersHandler = IS_LISTENERS_TAB_SUPPORTED
  ? [
      rest.post(
        '/api/process-instances/:instanceId/listeners',
        async (req, res, ctx) => {
          const body: {pageSize: number; flowNodeId: string} = await req.json();

          if (body.flowNodeId.includes('start')) {
            const listeners = mockListeners.slice(0, body.pageSize);
            return res(ctx.json({listeners, totalCount: listeners.length}));
          }
          return res(ctx.json([]));
        },
      ),
    ]
  : [];

const handlers: RequestHandler[] = [...listenersHandler];

export {handlers};
