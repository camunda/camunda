/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {RequestHandler, http, HttpResponse} from 'msw';
import * as processInstancesMocks from 'modules/mock-schema/mocks/process-instances';
import {IS_PROCESS_INSTANCES_ENABLED} from 'modules/featureFlags';

const handlers: RequestHandler[] = [];

if (IS_PROCESS_INSTANCES_ENABLED) {
  handlers.push(
    http.post('/internal/users/:userId/process-instances', () => {
      return HttpResponse.json(processInstancesMocks.processInstances);
    }),
  );
}

export {handlers};
