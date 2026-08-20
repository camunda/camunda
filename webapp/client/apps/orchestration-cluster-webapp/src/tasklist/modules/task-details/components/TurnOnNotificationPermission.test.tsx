/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {TurnOnNotificationPermission} from './TurnOnNotificationPermission';

describe('<TurnOnNotificationPermission />', () => {
	beforeEach(() => {
		localStorage.clear();
	});

	afterEach(() => {
		vi.unstubAllGlobals();
	});

	it('should show the prompt when permission is default and not dismissed', async () => {
		vi.stubGlobal('Notification', {permission: 'default', requestPermission: vi.fn()});

		const screen = await render(<TurnOnNotificationPermission />);

		await expect.element(screen.getByRole('status')).toBeVisible();
		await expect.element(screen.getByText(/Don't miss new assignments/)).toBeVisible();
		await expect.element(screen.getByRole('button', {name: /Turn on notifications/})).toBeVisible();
	});

	it('should not show the prompt when permission is granted', async () => {
		vi.stubGlobal('Notification', {permission: 'granted', requestPermission: vi.fn()});

		const screen = await render(<TurnOnNotificationPermission />);

		await expect.element(screen.getByRole('status')).not.toBeInTheDocument();
	});

	it('should not show the prompt when permission is denied', async () => {
		vi.stubGlobal('Notification', {permission: 'denied', requestPermission: vi.fn()});

		const screen = await render(<TurnOnNotificationPermission />);

		await expect.element(screen.getByRole('status')).not.toBeInTheDocument();
	});

	it('should hide the prompt and persist dismissal on close', async () => {
		vi.stubGlobal('Notification', {permission: 'default', requestPermission: vi.fn()});

		const screen = await render(<TurnOnNotificationPermission />);

		await expect.element(screen.getByRole('status')).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: /close notification/i}));

		await expect.element(screen.getByRole('status')).not.toBeInTheDocument();
		expect(getStateLocally('tasklist.areNativeNotificationsEnabled')).toBe(false);
	});

	it('should respect a prior dismissal', async () => {
		vi.stubGlobal('Notification', {permission: 'default', requestPermission: vi.fn()});
		storeStateLocally('tasklist.areNativeNotificationsEnabled', false);

		const screen = await render(<TurnOnNotificationPermission />);

		await expect.element(screen.getByRole('status')).not.toBeInTheDocument();
	});

	it('should request permission on action button click', async () => {
		const mockRequestPermission = vi.fn().mockResolvedValue('granted');
		vi.stubGlobal('Notification', {permission: 'default', requestPermission: mockRequestPermission});

		const screen = await render(<TurnOnNotificationPermission />);

		await userEvent.click(screen.getByRole('button', {name: /Turn on notifications/}));

		expect(mockRequestPermission).toHaveBeenCalledOnce();
		await expect.element(screen.getByRole('status')).not.toBeInTheDocument();
	});
});
