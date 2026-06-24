/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {describe, expect, beforeEach, afterEach} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {mockLoginEndpoint, mockLogoutEndpoint} from '#/shared-test-modules/mock-handlers';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {authenticationStore} from './authentication.store';

describe('authentication store', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		authenticationStore.reset();
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should assume that there is an existing session', () => {
		expect(authenticationStore.status).toBe('initial');
	});

	it('should login', async ({worker}) => {
		worker.use(
			mockLoginEndpoint({
				successResponse: new HttpResponse('', {status: 200}),
			}),
		);

		authenticationStore.disableSession();
		expect(authenticationStore.status).toBe('session-invalid');

		await authenticationStore.handleLogin('demo', 'demo');
		expect(authenticationStore.status).toBe('logged-in');
	});

	it('should handle login failure', async ({worker}) => {
		worker.use(
			mockLoginEndpoint({
				successResponse: new HttpResponse('', {status: 401}),
			}),
		);

		const result = await authenticationStore.handleLogin('demo', 'demo');

		expect(result).toStrictEqual({
			response: null,
			error: {
				variant: 'failed-response',
				response: expect.objectContaining({status: 401}),
				networkError: null,
			},
		});
		expect(authenticationStore.status).toBe('initial');
	});

	it('should logout', async ({worker}) => {
		worker.use(
			mockLoginEndpoint({
				successResponse: new HttpResponse('', {status: 200}),
			}),
			mockLogoutEndpoint({
				successResponse: new HttpResponse('', {status: 204}),
			}),
		);

		await authenticationStore.handleLogin('demo', 'demo');
		expect(authenticationStore.status).toBe('logged-in');

		await authenticationStore.handleLogout();
		expect(authenticationStore.status).toBe('logged-out');
	});

	it('should handle logout failure', async ({worker}) => {
		worker.use(mockLogoutEndpoint({successResponse: new HttpResponse('', {status: 500})}));

		const result = await authenticationStore.handleLogout();
		expect(result).toBeDefined();
	});

	it('should activate session', () => {
		expect(authenticationStore.status).toBe('initial');
		authenticationStore.activateSession();
		expect(authenticationStore.status).toBe('logged-in');
	});

	it('should disable session from initial state to session-invalid', () => {
		expect(authenticationStore.status).toBe('initial');
		authenticationStore.disableSession();
		expect(authenticationStore.status).toBe('session-invalid');
	});

	it('should disable session from logged-in state to session-expired', () => {
		authenticationStore.activateSession();
		expect(authenticationStore.status).toBe('logged-in');

		authenticationStore.disableSession();
		expect(authenticationStore.status).toBe('session-expired');
	});

	it('should not change status when already session-invalid', () => {
		authenticationStore.disableSession();
		expect(authenticationStore.status).toBe('session-invalid');

		authenticationStore.disableSession();
		expect(authenticationStore.status).toBe('session-invalid');
	});

	it('should not change status when already session-expired', () => {
		authenticationStore.activateSession();
		authenticationStore.disableSession();
		expect(authenticationStore.status).toBe('session-expired');

		authenticationStore.disableSession();
		expect(authenticationStore.status).toBe('session-expired');
	});

	it('should reset to initial status', () => {
		authenticationStore.activateSession();
		expect(authenticationStore.status).toBe('logged-in');

		authenticationStore.reset();
		expect(authenticationStore.status).toBe('initial');
	});
});
