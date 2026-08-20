/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, beforeEach, afterEach, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {notificationsStore} from './notifications.store';

describe('notifications store', () => {
	beforeEach(() => {
		notificationsStore.reset();
		vi.useFakeTimers();
	});

	afterEach(() => {
		vi.useRealTimers();
	});

	it('should display a notification and add it to the visible list', () => {
		expect(notificationsStore.notifications).toHaveLength(0);

		notificationsStore.displayNotification({
			kind: 'success',
			title: 'Task completed',
			isDismissable: true,
		});

		expect(notificationsStore.notifications).toHaveLength(1);
		expect(notificationsStore.notifications[0]).toMatchObject({
			kind: 'success',
			title: 'Task completed',
			isDismissable: true,
		});
	});

	it('should return a cleanup function that hides the notification', () => {
		const hide = notificationsStore.displayNotification({
			kind: 'info',
			title: 'Test',
			isDismissable: false,
		});

		expect(notificationsStore.notifications).toHaveLength(1);

		hide();

		expect(notificationsStore.notifications).toHaveLength(0);
	});

	it('should prepend new notifications to the list (most recent first)', () => {
		notificationsStore.displayNotification({kind: 'info', title: 'First', isDismissable: true});
		notificationsStore.displayNotification({kind: 'info', title: 'Second', isDismissable: true});

		expect(notificationsStore.notifications[0]!.title).toBe('Second');
		expect(notificationsStore.notifications[1]!.title).toBe('First');
	});

	it('should auto-remove a notification after 5 seconds', () => {
		notificationsStore.displayNotification({
			kind: 'success',
			title: 'Auto-remove me',
			isDismissable: false,
			autoRemove: true,
		});

		expect(notificationsStore.notifications).toHaveLength(1);

		vi.advanceTimersByTime(5100);

		expect(notificationsStore.notifications).toHaveLength(0);
	});

	it('should not auto-remove a notification', () => {
		notificationsStore.displayNotification({
			kind: 'info',
			title: 'Persistent',
			isDismissable: false,
			autoRemove: false,
		});

		vi.advanceTimersByTime(10000);

		expect(notificationsStore.notifications).toHaveLength(1);
	});

	it('should queue notifications when max visible notifications is reached', () => {
		for (let i = 1; i <= 5; i++) {
			notificationsStore.displayNotification({
				kind: 'info',
				title: `Notification ${i}`,
				isDismissable: true,
				autoRemove: false,
			});
		}

		expect(notificationsStore.notifications).toHaveLength(5);

		notificationsStore.displayNotification({
			kind: 'info',
			title: 'Queued notification',
			isDismissable: true,
			autoRemove: false,
		});

		expect(notificationsStore.notifications).toHaveLength(5);
		expect(notificationsStore.notifications.find((n) => n.title === 'Queued notification')).toBeUndefined();
	});

	it('should dequeue and show the next notification when a visible one is hidden', () => {
		for (let i = 1; i <= 5; i++) {
			notificationsStore.displayNotification({
				kind: 'info',
				title: `Notification ${i}`,
				isDismissable: true,
				autoRemove: false,
			});
		}

		notificationsStore.displayNotification({
			kind: 'success',
			title: 'Queued',
			isDismissable: true,
			autoRemove: false,
		});

		notificationsStore.notifications[0]!.hideNotification();

		expect(notificationsStore.notifications).toHaveLength(5);
		expect(notificationsStore.notifications.find((n) => n.title === 'Queued')).toBeDefined();
	});

	it('should reset all notifications', () => {
		notificationsStore.displayNotification({kind: 'info', title: 'A', isDismissable: true});
		notificationsStore.displayNotification({kind: 'info', title: 'B', isDismissable: true});

		expect(notificationsStore.notifications).toHaveLength(2);

		notificationsStore.reset();

		expect(notificationsStore.notifications).toHaveLength(0);
	});

	it('should assign an id and a date to each notification', () => {
		notificationsStore.displayNotification({
			kind: 'warning',
			title: 'With metadata',
			isDismissable: true,
		});

		const notification = notificationsStore.notifications[0]!;

		expect(notification.id).toBeTruthy();
		expect(notification.date).toBeGreaterThan(0);
	});
});
