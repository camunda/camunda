/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {RequestHandler, rest} from 'msw';

const handlers: RequestHandler[] = [
  rest.post(
    '/api/process-instances/:processInstanceId/modify',
    async (_, res, ctx) => {
      return res(ctx.delay(1000), ctx.json({}));
    }
  ),
];

export {handlers};
