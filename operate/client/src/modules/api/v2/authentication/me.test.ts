/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getMe, isAnonymousCurrentUser} from './me';
import {mockServer} from 'modules/mock-server/node';
import {authenticationStore} from 'modules/stores/authentication';
import {createUser} from 'modules/testUtils';
import {http, HttpResponse} from 'msw';

describe('getMe', () => {
  beforeEach(() => {
    authenticationStore.reset();
  });

  it('should return the current user of a caller with a session', async () => {
    const user = createUser();
    mockServer.use(
      http.get('/v2/authentication/me', () => HttpResponse.json(user)),
    );

    const {response, error} = await getMe();

    expect(error).toBeNull();
    expect(response).toEqual(user);
    expect(authenticationStore.status).toBe('logged-in');
  });

  it('should handle an anonymous current user like a 401', async () => {
    // An unprotected API answers a caller without a session with an anonymous user, not a 401.
    mockServer.use(
      http.get('/v2/authentication/me', () =>
        HttpResponse.json({...createUser(), username: null, anonymous: true}),
      ),
    );

    const {response, error} = await getMe();

    expect(response).toBeNull();
    expect(error?.response?.status).toBe(401);
    expect(authenticationStore.status).toBe('session-invalid');
  });
});

describe('isAnonymousCurrentUser', () => {
  it('should detect an anonymous current user', async () => {
    expect(await isAnonymousCurrentUser(Response.json({anonymous: true}))).toBe(
      true,
    );
  });

  it('should not treat a current user without the flag as anonymous', async () => {
    expect(
      await isAnonymousCurrentUser(Response.json({username: 'demo'})),
    ).toBe(false);
  });

  it('should not treat a body that is not JSON as anonymous', async () => {
    expect(await isAnonymousCurrentUser(new Response('not json'))).toBe(false);
  });
});
