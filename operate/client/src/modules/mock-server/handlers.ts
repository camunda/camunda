/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ListenerPayload} from 'modules/api/processInstances/fetchProcessInstanceListeners';
import {
  IS_LISTENERS_TAB_SUPPORTED,
  IS_VERSION_TAG_ENABLED,
} from 'modules/feature-flags';
import {mockListeners} from 'modules/mocks/mockListeners';
import {RequestHandler, rest} from 'msw';

const processVersionTagHandler = IS_VERSION_TAG_ENABLED
  ? [
      rest.get('/api/processes/:processId', async (req, res, ctx) => {
        const response = await ctx.fetch(req);
        const body = await response.json();

        return res(
          ctx.json({
            ...body,
            versionTag: 'myVersionTag',
          }),
        );
      }),
    ]
  : [];

const processInstancesVersionTagHandler = [
  rest.post('/api/processes/grouped', async (req, res, ctx) => {
    const response = await ctx.fetch(req);
    const processDefinitions = await response.json();

    const processesDefinitionsWithVersionTags = processDefinitions.map(
      (processDefinition: any) => {
        const processesWithVersionTag = processDefinition.processes.map(
          (process: any) => {
            if (process.bpmnProcessId === 'complexProcess') {
              return {...process, versionTag: 'myVersionTag'};
            } else {
              return {...process, versionTag: null};
            }
          },
        );
        return {...processDefinition, processes: processesWithVersionTag};
      },
    );
    return res(ctx.json(processesDefinitionsWithVersionTags));
  }),
];

const listenersHandler = IS_LISTENERS_TAB_SUPPORTED
  ? [
      rest.post(
        '/api/process-instances/:instanceId/listeners',
        async (req, res, ctx) => {
          const body: ListenerPayload = await req.json();

          if (body.flowNodeId.includes('start')) {
            let listeners: ListenerEntity[] = [];
            if (body.searchAfter) {
              listeners = mockListeners.slice(
                body.pageSize,
                (body.pageSize || 6) * 2,
              );
            } else {
              listeners = mockListeners.slice(0, body.pageSize);
            }
            return res(ctx.json({listeners, totalCount: listeners.length}));
          }
          return res(ctx.json([]));
        },
      ),
    ]
  : [];

const handlers: RequestHandler[] = [
  ...processVersionTagHandler,
  ...processInstancesVersionTagHandler,
  ...listenersHandler,
];

export {handlers};
