/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IS_VERSION_TAG_ENABLED} from 'modules/feature-flags';
import {RequestHandler, rest} from 'msw';

const processVersionTagHandler = [
  rest.get('/api/processes/:processId', async (req, res, ctx) => {
    const response = await ctx.fetch(req);
    const body = await response.json();

    return res(
      ctx.json({
        ...body,
        versionTag: 'myVersionTag',
      }),
    );
  }),
];

const handlers: RequestHandler[] = IS_VERSION_TAG_ENABLED
  ? processVersionTagHandler
  : [];

export {handlers};
