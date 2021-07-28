/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {RequestHandler, rest} from 'msw';

const handlers: RequestHandler[] = [
  rest.get('/api/authentications/user', async (req, res, ctx) => {
    const response = await ctx.fetch(req);
    const body = await response.json();
    if (body.username === 'act') {
      return res(
        ctx.json({
          ...body,
          roles: ['view'],
        })
      );
    } else {
      return res(ctx.json(body));
    }
  }),
];

export {handlers};
