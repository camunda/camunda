/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {IS_ATTACHMENTS_TAB_ENABLED} from 'modules/featureFlags';
import {http, HttpResponse, RequestHandler} from 'msw';

const handlers: RequestHandler[] = [];

if (IS_ATTACHMENTS_TAB_ENABLED) {
  handlers.push(
    http.get('/v2/tasks/:taskId/attachments', () => {
      return HttpResponse.json([
        {
          id: 'attachment-1',
          fileName: 'file-1.txt',
          contentType: 'text/plain',
        },
        {
          id: 'attachment-2',
          fileName: 'file-2.txt',
          contentType: 'text/plain',
        },
      ]);
    }),
  );
}

export {handlers};
