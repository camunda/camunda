/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {rest} from 'msw';

const handlers = [
  rest.get('/v2/me', (_, res, ctx) => {
    return res(
      ctx.status(302),
      ctx.set('Location', '/api/authentications/user'),
    );
  }),
  rest.get('/api/authentications/user', async (req, res, ctx) => {
    const response = await ctx.fetch(req);
    const user = await response.json();

    return res(
      ctx.json({
        ...user,
        permissions: [
          {
            type: 'ACCESS',
            resourceIds: ['operate'],
          },
        ],
      }),
    );
  }),
];

export {handlers};
