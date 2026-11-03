/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect, it, vi} from 'vitest';
import {authenticationStore} from '#/shared/auth/authentication.store';
import {request} from './request';

const CURRENT_USER_URL = '/v2/authentication/me';

describe('request', () => {
	afterEach(() => {
		vi.restoreAllMocks();
	});

	it('should handle an anonymous current user like a 401', async () => {
		vi.spyOn(globalThis, 'fetch').mockResolvedValue(Response.json({isAnonymous: true, username: null}));
		const disableSession = vi.spyOn(authenticationStore, 'disableSession').mockImplementation(() => {});

		const {response, error} = await request(CURRENT_USER_URL);

		expect(response).toBeNull();
		expect(error?.variant).toBe('failed-response');
		expect(disableSession).toHaveBeenCalled();
	});

	it('should return an authenticated current user', async () => {
		vi.spyOn(globalThis, 'fetch').mockResolvedValue(Response.json({isAnonymous: false, username: 'demo'}));

		const {response, error} = await request(CURRENT_USER_URL);

		expect(error).toBeNull();
		expect(await response?.json()).toMatchObject({username: 'demo'});
	});

	it('should not check other endpoints for an anonymous user', async () => {
		vi.spyOn(globalThis, 'fetch').mockResolvedValue(Response.json({isAnonymous: true}));

		const {error} = await request('/v2/process-instances/search');

		expect(error).toBeNull();
	});

	it('should not treat a current user body that is not JSON as anonymous', async () => {
		vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('not json'));

		const {error} = await request(CURRENT_USER_URL);

		expect(error).toBeNull();
	});
});
