/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createRoot} from 'react-dom/client';
import {act} from 'react';
import {vi, describe, it, expect, beforeEach, afterEach} from 'vitest';
import {authenticationStore} from '../stores/authentication';
import {SessionWatcher} from './SessionWatcher';

const mockNavigate = vi.fn();

vi.mock('@tanstack/react-router', async (importOriginal) => {
	const original = await importOriginal<typeof import('@tanstack/react-router')>();
	return {
		...original,
		useNavigate: () => mockNavigate,
	};
});

describe('SessionWatcher', () => {
	let container: HTMLDivElement;
	let root: ReturnType<typeof createRoot>;

	beforeEach(() => {
		container = document.createElement('div');
		document.body.appendChild(container);

		act(() => {
			root = createRoot(container);
			root.render(<SessionWatcher />);
		});

		authenticationStore.reset();
		mockNavigate.mockReset();
	});

	afterEach(() => {
		act(() => {
			root.unmount();
		});
		document.body.removeChild(container);
		authenticationStore.reset();
	});

	it('should navigate to /login when session expires', () => {
		act(() => {
			authenticationStore.setStatus('session-expired');
		});

		expect(mockNavigate).toHaveBeenCalledWith({to: '/login', replace: true});
	});

	it('should navigate to /login when session is invalid', () => {
		act(() => {
			authenticationStore.setStatus('session-invalid');
		});

		expect(mockNavigate).toHaveBeenCalledWith({to: '/login', replace: true});
	});

	it('should not navigate when status is initial', () => {
		act(() => {
			authenticationStore.reset();
		});

		expect(mockNavigate).toHaveBeenCalledTimes(0);
	});

	it('should not navigate when status is logged-in', () => {
		act(() => {
			authenticationStore.setStatus('logged-in');
		});

		expect(mockNavigate).toHaveBeenCalledTimes(0);
	});

	it('should not navigate when status is logged-out', () => {
		act(() => {
			authenticationStore.setStatus('logged-out');
		});

		expect(mockNavigate).toHaveBeenCalledTimes(0);
	});
});
