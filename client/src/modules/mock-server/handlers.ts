/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {HAS_PARENT_INSTANCE_ID} from 'modules/feature-flags';
import {processesStore} from 'modules/stores/processes';
import {RequestHandler, rest} from 'msw';

const handlers: RequestHandler[] = [];

if (HAS_PARENT_INSTANCE_ID) {
  const PARENT_INSTANCE_ID = '000';
  handlers.push(
    rest.post('/api/process-instances', async (req: any, res, ctx) => {
      if (req.body.query.parentInstanceId === PARENT_INSTANCE_ID) {
        req.body.query.processIds = processesStore.versionsByProcess[
          'called-process'
        ].map((process) => process.id);
      }

      if (req.body.sorting.sortBy === 'parentInstanceId') {
        req.body.sorting.sortBy = 'processName';
      }

      const response = await ctx.fetch(req);
      const responseBody = await response.json();

      responseBody.processInstances.map((instance: any) => {
        if (instance.bpmnProcessId === 'called-process') {
          instance.parentInstanceId = PARENT_INSTANCE_ID;
        } else {
          instance.parentInstanceId = null;
        }

        return instance;
      });

      return res(ctx.json(responseBody));
    })
  );
}

export {handlers};
