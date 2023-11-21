/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {IS_INSTANCE_MIGRATION_API_MOCKED} from 'modules/feature-flags';
import {RequestHandler, RestRequest, rest} from 'msw';

const mockBatchOperation = IS_INSTANCE_MIGRATION_API_MOCKED
  ? [
      rest.post(
        'api/process-instances/batch-operation',
        async (req: RestRequest<any>, res, ctx) => {
          const request = await req.json();

          if (request.operationType !== 'MIGRATE_PROCESS_INSTANCE') {
            req.passthrough();
          }

          return res(
            ctx.json({
              id: '0b10e52c-a13c-424a-b83a-057ae99c64af',
              name: null,
              type: 'MIGRATE_PROCESS_INSTANCE',
              startDate: '2023-11-16T09:45:05.427+0100',
              endDate: null,
              username: 'demo',
              instancesCount: 1,
              operationsTotalCount: 1,
              operationsFinishedCount: 0,
            }),
          );
        },
      ),
    ]
  : [];

const handlers: RequestHandler[] = [...mockBatchOperation];

export {handlers};
