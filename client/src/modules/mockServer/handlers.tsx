/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ProcessInstance} from 'modules/types';
import {RequestHandler, rest} from 'msw';
import * as processInstancesMocks from 'modules/mock-schema/mocks/process-instances';
import {IS_PROCESS_INSTANCES_ENABLED} from 'modules/featureFlags';

let handlers: RequestHandler[] = [];

if (IS_PROCESS_INSTANCES_ENABLED) {
  handlers.push(
    rest.post('/internal/users/:userId/process-instances', (_, res, ctx) => {
      return res(
        ctx.json<ProcessInstance[]>(processInstancesMocks.processInstances),
      );
    }),
  );
}

export {handlers};
