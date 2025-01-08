/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {setupServer} from 'msw/node';
import {http, HttpResponse, HttpHandler} from 'msw';
import {currentUser} from '../mock-schema/mocks/current-user';
import type {CurrentUser} from '../types';

export function createMockAuthenticationMeHandler(
  currentUser: CurrentUser,
  overrides?: object,
): HttpHandler {
  return http.get(
    '/v2/authentication/me',
    () => {
      return HttpResponse.json({
        ...currentUser,
        userKey: 112233,
        username: currentUser.displayName,
        email: 'user@camunda.test',
        canLogout: true,
        ...overrides,
      });
    },
    {once: false},
  );
}

export const nodeMockServer = setupServer(
  createMockAuthenticationMeHandler(currentUser),
);
