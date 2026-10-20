/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {render} from 'vitest-browser-react';
import {Toaster, toast} from '@camunda/design-system';
import {it} from '#/vitest-modules/test-extend';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {Notifications} from './Notifications';

const Wrapper: React.FC<{children?: React.ReactNode}> = ({children}) => (
	<>
		<Toaster />
		{children}
	</>
);

describe('<Notifications /> (design system)', () => {
	afterEach(() => {
		notificationsStore.reset();
		toast.dismiss();
	});

	it('should render title and subtitle of a notification', async () => {
		const screen = await render(<Notifications />, {wrapper: Wrapper});

		notificationsStore.displayNotification({
			kind: 'success',
			title: 'Operation created',
			subtitle: 'Batch finished',
			isDismissable: true,
		});

		await expect.element(screen.getByText('Operation created')).toBeVisible();
		await expect.element(screen.getByText('Batch finished')).toBeVisible();
	});

	it('should render notifications raised before mounting', async () => {
		notificationsStore.displayNotification({kind: 'error', title: 'Early error', isDismissable: true});

		const screen = await render(<Notifications />, {wrapper: Wrapper});

		await expect.element(screen.getByText('Early error')).toBeVisible();
	});

	it('should remove the toast when the store hides the notification', async () => {
		const screen = await render(<Notifications />, {wrapper: Wrapper});
		const hide = notificationsStore.displayNotification({
			kind: 'info',
			title: 'Temporary',
			isDismissable: false,
			autoRemove: false,
		});

		await expect.element(screen.getByText('Temporary')).toBeVisible();

		hide();

		await expect.element(screen.getByText('Temporary')).not.toBeInTheDocument();
		expect(notificationsStore.notifications).toHaveLength(0);
	});

	it('should let the user dismiss a toast with the dismiss button', async () => {
		const screen = await render(<Notifications />, {wrapper: Wrapper});
		notificationsStore.displayNotification({
			kind: 'warning',
			title: 'Dismiss me',
			isDismissable: true,
			autoRemove: false,
		});

		await expect.element(screen.getByText('Dismiss me')).toBeVisible();
		await userEvent.click(screen.getByRole('button', {name: 'Dismiss'}));

		await expect.element(screen.getByText('Dismiss me')).not.toBeInTheDocument();
	});

	it('should render an action button and fire its callback', async () => {
		const onActionButtonClick = vi.fn();
		const screen = await render(<Notifications />, {wrapper: Wrapper});
		notificationsStore.displayNotification({
			kind: 'success',
			title: 'Operation created',
			isDismissable: true,
			isActionable: true,
			actionButtonLabel: 'Go to operation details',
			onActionButtonClick,
			autoRemove: false,
		});

		await userEvent.click(screen.getByRole('button', {name: 'Go to operation details'}));

		expect(onActionButtonClick).toHaveBeenCalledOnce();
	});

	it('should auto-remove notifications after the store timeout', async () => {
		vi.useFakeTimers({toFake: ['setInterval', 'clearInterval']});
		try {
			const screen = await render(<Notifications />, {wrapper: Wrapper});
			notificationsStore.displayNotification({kind: 'info', title: 'Fades', isDismissable: true});

			await expect.element(screen.getByText('Fades')).toBeVisible();

			vi.advanceTimersByTime(5500);

			await expect.element(screen.getByText('Fades')).not.toBeInTheDocument();
		} finally {
			vi.useRealTimers();
		}
	});
});
