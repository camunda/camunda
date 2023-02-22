/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  IS_PROCESS_DEFINITION_DELETION_ENABLED,
  IS_DECISION_DEFINITION_DELETION_ENABLED,
} from 'modules/feature-flags';
import {RequestHandler, rest} from 'msw';

const mockBatchOperations =
  IS_PROCESS_DEFINITION_DELETION_ENABLED ||
  IS_DECISION_DEFINITION_DELETION_ENABLED
    ? [
        rest.post('/api/batch-operations', async (req, res, ctx) => {
          const response = await ctx.fetch(req);
          const batchOperations = await response.json();

          return res(
            ctx.json([
              ...(IS_PROCESS_DEFINITION_DELETION_ENABLED
                ? [
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
                  ]
                : []),
              ...(IS_DECISION_DEFINITION_DELETION_ENABLED
                ? [
                    {
                      id: '5de66f22-a438-40f8-a89c-fn298fn23988',
                      name: 'DecisionDefinitionA - version 1',
                      type: 'DELETE_DECISION_DEFINITION',
                      startDate: '2023-02-16T14:23:45.306+0100',
                      endDate: null,
                      instancesCount: 23,
                      operationsTotalCount: 23,
                      operationsFinishedCount: 10,
                    },
                    {
                      id: '01d2e96c-00d6-4446-b3db-2n9r87fh9028',
                      name: 'DecisionDefinitionA - version 2',
                      type: 'DELETE_DECISION_DEFINITION',
                      startDate: '2023-02-16T14:23:45.306+0100',
                      endDate: '2023-02-16T14:28:45.306+0100',
                      instancesCount: 42,
                      operationsTotalCount: 42,
                      operationsFinishedCount: 42,
                    },
                  ]
                : []),

              ...batchOperations,
            ])
          );
        }),
      ]
    : [];

const mockDeleteDecisionDefinition = IS_DECISION_DEFINITION_DELETION_ENABLED
  ? [
      rest.delete(
        '/api/decisions/:decisionDefinitionId',
        async (_, res, ctx) => {
          return res(
            ctx.json({
              id: '5de66f22-a438-40f8-a89c-2983fhn283h8',
              name: 'MyDecisionDefinition - Version 1',
              type: 'DELETE_DECISION_DEFINITION',
              startDate: '2023-02-16T14:23:45.306+0100',
              endDate: null,
              instancesCount: 23,
              operationsTotalCount: 23,
              operationsFinishedCount: 0,
            })
          );
        }
      ),
    ]
  : [];

const handlers: RequestHandler[] = [
  ...mockBatchOperations,
  ...mockDeleteDecisionDefinition,
];
export {handlers};
