/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {USE_NEW_APP_HEADER} from 'modules/feature-flags';
import {RequestHandler, rest} from 'msw';

const handlers: RequestHandler[] = [
  ...(USE_NEW_APP_HEADER
    ? [
        rest.get('/api/authentications/user', async (req, res, ctx) => {
          const response = await ctx.fetch(req);
          const userInfo = await response.json();

          return res(
            ctx.json({
              ...userInfo,
              c8Links: {
                operate: 'https://link-to-operate',
                tasklist: 'https://link-to-tasklist',
                modeler: 'https://link-to-modeler',
                console: 'https://link-to-console',
                optimize: 'https://link-to-optimize',
              },
            })
          );
        }),
      ]
    : []),
];

export {handlers};
