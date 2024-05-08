/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {RequestHandler, rest} from 'msw';
import {IS_OPERATIONS_PANEL_IMPROVEMENT_ENABLED} from 'modules/feature-flags';
import {mockBatchStatus} from 'modules/mocks/mockBatchStatus';

const batchOperationHandlers = IS_OPERATIONS_PANEL_IMPROVEMENT_ENABLED
  ? [
      rest.post('api/batch-operations', async (req, res, ctx) => {
        const originalResponse = await ctx.fetch(req);
        let originalResponseData = await originalResponse.json();
        originalResponseData = originalResponseData.map(
          (r: any, index: number) => ({
            ...r,
            ...(mockBatchStatus[index] || mockBatchStatus.at(-1)),
          }),
        );

        return res(ctx.json(originalResponseData));
      }),
    ]
  : [];

const handlers: RequestHandler[] = [...batchOperationHandlers];

export {handlers};
