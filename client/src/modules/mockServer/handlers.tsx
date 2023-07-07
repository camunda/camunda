/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Process} from 'modules/types';
import {RequestHandler, rest} from 'msw';
import * as formMocks from 'modules/mock-schema/mocks/form';

const handlers: RequestHandler[] = [
  rest.get('/v1/internal/processes', async (req, res, ctx) => {
    const originalResponse = await ctx.fetch(req);
    const originalResponseData = (await originalResponse.json()) as Process[];

    return res(
      ctx.json(
        originalResponseData.map((process: Process, index) => ({
          ...process,
          bpmnProcessId: process.processDefinitionKey,
          processDefinitionKey: '2251799813685255',
          formId: index % 2 === 0 ? 'userTaskForm_3j0n396' : null,
        })),
      ),
    );
  }),
  rest.get('/v1/forms/userTaskForm_3j0n396', async (_, res, ctx) => {
    return res(ctx.json(formMocks.form));
  }),
];

export {handlers};
