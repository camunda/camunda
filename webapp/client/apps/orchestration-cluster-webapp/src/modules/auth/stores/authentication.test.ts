/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {http, HttpResponse} from 'msw';
import {describe, it, expect, beforeEach} from 'vitest';
import {authenticationStore} from './authentication';
import {it as itWithWorker} from '#/vitest-modules/test-extend';

describe('authentication store', () => {
	beforeEach(() => {
		authenticationStore.reset();
	});

	it('should assume that there is an existing session', () => {
		expect(authenticationStore.status).toBe('initial');
	});

	itWithWorker('should login', async ({worker}) => {
		worker.use(http.post('/login', () => new HttpResponse(''), {once: true}));

		authenticationStore.setStatus('session-invalid');

		await authenticationStore.handleLogin('demo', 'demo');

		expect(authenticationStore.status).toBe('logged-in');
	});

	itWithWorker('should handle login failure', async ({worker}) => {
		worker.use(http.post('/login', () => new HttpResponse('', {status: 401}), {once: true}));

		const result = await authenticationStore.handleLogin('demo', 'demo');

		expect(result).toStrictEqual({
			error: {
				variant: 'failed-response',
				response: expect.objectContaining({status: 401}),
				networkError: null,
			},
		});
		expect(authenticationStore.status).toBe('initial');
	});

	itWithWorker('should logout', async ({worker}) => {
		worker.use(http.post('/logout', () => new HttpResponse(''), {once: true}));

		authenticationStore.setStatus('logged-in');

		await authenticationStore.handleLogout();

		expect(authenticationStore.status).toBe('logged-out');
	});

	itWithWorker('should return an error on logout failure', async ({worker}) => {
		worker.use(http.post('/logout', () => new HttpResponse('', {status: 500}), {once: true}));

		expect(await authenticationStore.handleLogout()).toBeDefined();
	});

	it('should set status to session-invalid when disabling session from initial state', () => {
		authenticationStore.disableSession();

		expect(authenticationStore.status).toBe('session-invalid');
	});

	it('should set status to session-expired when disabling session from logged-in state', () => {
		authenticationStore.setStatus('logged-in');

		authenticationStore.disableSession();

		expect(authenticationStore.status).toBe('session-expired');
	});

	it('should not change status when session is already disabled', () => {
		authenticationStore.setStatus('session-invalid');
		authenticationStore.disableSession();
		expect(authenticationStore.status).toBe('session-invalid');

		authenticationStore.setStatus('session-expired');
		authenticationStore.disableSession();
		expect(authenticationStore.status).toBe('session-expired');
	});

	// TODO: add tests for third-party session handling (canLogout / isLoginDelegated) once getClientConfig is supported
	// https://github.com/camunda/camunda/issues/51322
});
