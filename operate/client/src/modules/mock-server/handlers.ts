/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IS_LATEST_MIGRATION_DATE_ENABLED} from 'modules/feature-flags';
import {RequestHandler, rest} from 'msw';

const processInstanceHandler = rest.get(
  '/api/process-instances/:id',
  async (req, res, ctx) => {
    const id = req.params.id as string;

    if (id.match(/^[0-9]{16,19}$/)) {
      const response = await ctx.fetch(req);
      const instance: ProcessInstanceEntity = await response.json();

      const {operations} = instance;

      const instanceWithCompletedDate: ProcessInstanceEntity = {
        ...instance,
        operations: [
          ...operations,
          {
            id: '07b22091-bd87-47dd-81c1-bd09ab81e141',
            batchOperationId: 'a7928686-7e34-4d23-89e0-6497f783fe18',
            type: 'MIGRATE_PROCESS_INSTANCE',
            state: 'COMPLETED',
            errorMessage: null,
            completedDate: '2024-06-10T09:14:02.660+0000',
          },
        ],
      };

      return res(ctx.json(instanceWithCompletedDate));
    }

    req.passthrough();
  },
);

const handlers: RequestHandler[] = IS_LATEST_MIGRATION_DATE_ENABLED
  ? [processInstanceHandler]
  : [];

export {handlers};
