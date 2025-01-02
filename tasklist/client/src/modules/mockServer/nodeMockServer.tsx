/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {setupServer} from 'msw/node';
import {http, HttpResponse} from 'msw';
import {currentUser} from '../mock-schema/mocks/current-user';

const mockV2AuthenticationMeHandler = http.get(
  '/v2/authentication/me',
  () => {
    return HttpResponse.json({
      ...currentUser,
      userKey: 112233,
      username: currentUser.displayName,
      email: 'user@camunda.test',
      canLogout: true,
    });
  },
  {once: false},
);

const nodeMockServer = setupServer(mockV2AuthenticationMeHandler);

export {nodeMockServer};
