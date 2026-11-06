/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {afterEach, beforeEach, describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {authenticationStore} from '#/shared/auth/authentication.store';
import {mockCurrentUserEndpoint, mockQueryProcessInstancesEndpoint} from '#/shared-test-modules/mock-handlers';
import {createAnonymousCurrentUser, createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {endpoints} from './endpoints';
import {request} from './request';

describe('request', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		authenticationStore.reset();
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should handle an anonymous current user like a 401', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createAnonymousCurrentUser())}));

		const {response, error} = await request(endpoints.getCurrentUser());

		expect(response).toBeNull();
		expect(error?.variant).toBe('failed-response');
		expect(authenticationStore.status).toBe('session-invalid');
	});

	it('should return an authenticated current user', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser({username: 'demo'}))}));

		const {response, error} = await request(endpoints.getCurrentUser());

		expect(error).toBeNull();
		expect(await response?.json()).toMatchObject({username: 'demo'});
		expect(authenticationStore.status).toBe('logged-in');
	});

	it('should not check other endpoints for an anonymous user', async ({worker}) => {
		worker.use(mockQueryProcessInstancesEndpoint({successResponse: HttpResponse.json({isAnonymous: true})}));

		const {error} = await request(endpoints.queryProcessInstances({}));

		expect(error).toBeNull();
		expect(authenticationStore.status).toBe('logged-in');
	});

	it('should not treat a current user body that is not JSON as anonymous', async ({worker}) => {
		worker.use(mockCurrentUserEndpoint({successResponse: new HttpResponse('not json')}));

		const {error} = await request(endpoints.getCurrentUser());

		expect(error).toBeNull();
		expect(authenticationStore.status).toBe('logged-in');
	});
});
