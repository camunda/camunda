/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {IS_PROCESS_DEFINITION_DELETION_ENABLED} from 'modules/feature-flags';
import {RequestHandler, rest} from 'msw';

const mockBatchOperations = IS_PROCESS_DEFINITION_DELETION_ENABLED
  ? [
      rest.post('/api/batch-operations', async (req, res, ctx) => {
        const response = await ctx.fetch(req);
        const batchOperations = await response.json();

        return res(
          ctx.json([
            {
              id: '5de66f22-a438-40f8-a89c-904g2dgfjm28',
              name: 'ProcessDefinitionA - version 1',
              type: 'DELETE_PROCESS_DEFINITION',
              startDate: '2023-02-16T14:23:45.306+0100',
              endDate: null,
              instancesCount: 1,
              operationsTotalCount: 1,
              operationsFinishedCount: 0,
            },
            {
              id: '01d2e96c-00d6-4446-b3db-gfjm28904g2d',
              name: 'ProcessDefinitionA - version 2',
              type: 'DELETE_PROCESS_DEFINITION',
              startDate: '2023-02-16T14:23:45.306+0100',
              endDate: '2023-02-16T14:28:45.306+0100',
              instancesCount: 1,
              operationsTotalCount: 1,
              operationsFinishedCount: 1,
            },
            ...batchOperations,
          ]),
        );
      }),
    ]
  : [];

const mockDeleteProcessDefinition = IS_PROCESS_DEFINITION_DELETION_ENABLED
  ? [
      rest.delete(
        '/api/processes/:processDefinitionId',
        async (_, res, ctx) => {
          return res(
            ctx.json({
              id: '5de66f22-a438-40f8-a89c-2983fhn283h8',
              name: 'MyProcessDefinition - Version 1',
              type: 'DELETE_PROCESS_DEFINITION',
              startDate: '2023-02-16T14:23:45.306+0100',
              endDate: null,
              instancesCount: 23,
              operationsTotalCount: 23,
              operationsFinishedCount: 0,
            }),
          );
        },
      ),
    ]
  : [];

const handlers: RequestHandler[] = [
  ...mockBatchOperations,
  ...mockDeleteProcessDefinition,
];

export {handlers};
