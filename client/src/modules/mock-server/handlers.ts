/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  IS_PROCESS_DEFINITION_DELETION_ENABLED,
  IS_VARIABLE_VALUE_IN_FILTER_ENABLED,
} from 'modules/feature-flags';
import {RequestHandler, RestRequest, rest} from 'msw';

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

// This mock only provides the first variable value as API parameter
// in case the user enters a list of values.
const mockProcessInstances = IS_VARIABLE_VALUE_IN_FILTER_ENABLED
  ? [
      rest.post(
        '/api/process-instances',
        async (req: RestRequest<any>, res, ctx) => {
          const variable = req.body?.query?.variable;

          if (variable !== undefined) {
            req.body.query.variable = {
              name: variable.name,
              value: variable.values[0],
            };
          }
          const response = await ctx.fetch(req);

          if (response.status !== 200) {
            return res(ctx.status(response.status));
          }
          return res(ctx.json(await response.json()));
        },
      ),
    ]
  : [];

// This mock only provides the first variable value as API parameter
// in case the user enters a list of values.
const mockProcessInstancesStatistics = IS_VARIABLE_VALUE_IN_FILTER_ENABLED
  ? [
      rest.post(
        '/api/process-instances/statistics',
        async (req: RestRequest<any>, res, ctx) => {
          const variable = req.body?.variable;

          if (variable !== undefined) {
            req.body.variable = {
              name: variable.name,
              value: variable.values[0],
            };
          }
          const response = await ctx.fetch(req);

          if (response.status !== 200) {
            return res(ctx.status(response.status));
          }
          return res(ctx.json(await response.json()));
        },
      ),
    ]
  : [];

const handlers: RequestHandler[] = [
  ...mockBatchOperations,
  ...mockDeleteProcessDefinition,
  ...mockProcessInstances,
  ...mockProcessInstancesStatistics,
];

export {handlers};
