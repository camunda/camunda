/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// DS-only toast dispatch for Tasklist call sites. notifications.store.ts and
// its <Notifications/> renderer stay untouched — Operate and the Carbon
// Tasklist path keep going through them unconditionally. `notify()` takes
// the exact same argument shape as notificationsStore.displayNotification
// so call sites are a drop-in rename, and branches on the flag itself so
// each call site doesn't need its own if/else. The DS's own <Toaster/>
// (AppToaster.tsx) handles queueing, stacking, and auto-dismiss — no
// store/queue needed on this path.
import {toast} from '@camunda/design-system';
import {featureFlags} from '#/shared/feature-flags';
import {notificationsStore, type Notification} from '#/shared/notifications/notifications.store';

type NotifyOptions = Omit<Notification, 'date' | 'id' | 'hideNotification' | 'kind'> & {
	kind: 'info' | 'success' | 'error';
};

const notify = ({
	kind,
	title,
	subtitle,
	isDismissable = true,
	isActionable,
	actionButtonLabel,
	onActionButtonClick,
	autoRemove,
}: NotifyOptions) => {
	if (!featureFlags.dsTasklistUI) {
		notificationsStore.displayNotification({
			kind,
			title,
			subtitle,
			isDismissable,
			isActionable,
			actionButtonLabel,
			onActionButtonClick,
			autoRemove,
		});
		return;
	}

	// The DS Toaster always renders a close button (no per-toast override in
	// its ToastOptions) — isDismissable has no effect here, only on the
	// Carbon branch above, where Notification.tsx's hideCloseButton still
	// honors it.
	toast[kind](title, {
		description: subtitle,
		action:
			isActionable === true && actionButtonLabel !== undefined && onActionButtonClick !== undefined
				? {label: actionButtonLabel, onClick: onActionButtonClick}
				: undefined,
	});
};

export {notify};
