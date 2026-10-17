/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {reaction} from 'mobx';
import {toast} from '@camunda/design-system';
import {notificationsStore, type Notification} from '#/shared/notifications/notifications.store';

const toastByKind = {
	error: toast.error,
	info: toast.info,
	'info-square': toast.info,
	success: toast.success,
	warning: toast.warning,
	'warning-alt': toast.warning,
} satisfies Record<Notification['kind'], typeof toast.info>;

const showToast = ({kind, title, subtitle, isActionable, actionButtonLabel, onActionButtonClick}: Notification) =>
	toastByKind[kind](title, {
		description: subtitle,
		duration: Infinity,
		action: isActionable ? {label: actionButtonLabel ?? '', onClick: () => onActionButtonClick?.()} : undefined,
	});

const Notifications: React.FC = () => {
	useEffect(() => {
		const shown = new Map<string, string | number>();

		const sync = (notifications: Notification[]) => {
			const currentIds = new Set(notifications.map(({id}) => id));

			for (const [id, toastId] of shown) {
				if (!currentIds.has(id)) {
					shown.delete(id);
					toast.dismiss(toastId);
				}
			}

			for (const notification of [...notifications].reverse()) {
				if (!shown.has(notification.id)) {
					shown.set(notification.id, showToast(notification));
				}
			}
		};

		const dispose = reaction(() => notificationsStore.notifications.slice(), sync, {
			fireImmediately: true,
		});

		return () => {
			dispose();
			for (const toastId of shown.values()) {
				toast.dismiss(toastId);
			}
		};
	}, []);

	return null;
};

export {Notifications};
