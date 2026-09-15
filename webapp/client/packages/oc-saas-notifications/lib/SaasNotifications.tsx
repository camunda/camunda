/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useContext, useMemo, useState} from 'react';
import {Button, NotificationBell, NotificationsPanel, type NavNotification} from '@camunda/design-system';
import C3NotificationProvider, {
	C3NotificationContext,
} from '@camunda/camunda-composite-components/lib/esm/src/components/c3-navigation/c3-notification-provider/c3-notification-provider.js';

type SaasNotificationsLabels = {
	title: string;
	loading: string;
	empty: string;
	dismissAll: string;
};

type SaasNotificationsProps = {
	labels: SaasNotificationsLabels;
	locale?: Intl.LocalesArgument;
};

const SaasNotificationsContent: React.FC<SaasNotificationsProps> = ({labels, locale}) => {
	const {enabled, isFetching, notifications, markAllAsRead, dismiss, dismissAll, analytics} =
		useContext(C3NotificationContext);
	const [isOpen, setIsOpen] = useState(false);
	const [unreadAtOpen, setUnreadAtOpen] = useState<Set<string>>(() => new Set());
	const unreadNotifications = useMemo(() => notifications.filter(({state}) => state === 'new'), [notifications]);

	const dateFormatter = useMemo(
		() =>
			new Intl.DateTimeFormat(locale, {
				dateStyle: 'medium',
				timeStyle: 'short',
			}),
		[locale],
	);
	const navNotifications = useMemo<NavNotification[]>(
		() =>
			[...notifications]
				.sort((left, right) => right.timestamp - left.timestamp)
				.map((notification) => ({
					key: notification.uuid,
					title: notification.title,
					description: notification.description,
					timestamp: dateFormatter.format(notification.timestamp),
					isRead: notification.state !== 'new' && !unreadAtOpen.has(notification.uuid),
					href: notification.meta?.href,
					onClick:
						notification.meta?.identifier === undefined
							? undefined
							: () => {
									analytics('notification-clicked-cta', notification.meta?.identifier);
								},
				})),
		[analytics, dateFormatter, notifications, unreadAtOpen],
	);

	const handleOpenChange = useCallback(
		(isNextOpen: boolean) => {
			setIsOpen(isNextOpen);

			if (!isNextOpen) {
				if (unreadNotifications.length > 0) {
					markAllAsRead(unreadNotifications);
				}
				setUnreadAtOpen(new Set());
				return;
			}

			if (unreadNotifications.length > 0) {
				setUnreadAtOpen(new Set(unreadNotifications.map(({uuid}) => uuid)));
				markAllAsRead(unreadNotifications);
			}
			if (enabled) {
				analytics('notification-panel-opened');
			}
		},
		[analytics, enabled, markAllAsRead, unreadNotifications],
	);

	return (
		<>
			<NotificationBell
				label={labels.title}
				unreadCount={isFetching ? 0 : unreadNotifications.length}
				isActive={isOpen}
				onClick={() => handleOpenChange(!isOpen)}
			/>
			<NotificationsPanel
				title={labels.title}
				notifications={navNotifications}
				open={isOpen}
				onOpenChange={handleOpenChange}
				onDismiss={(key) => {
					const notification = notifications.find(({uuid}) => uuid === key);
					if (notification !== undefined) {
						dismiss(notification);
					}
				}}
				emptyMessage={isFetching ? labels.loading : labels.empty}
				headerAction={
					notifications.length > 0 ? (
						<Button type="button" variant="ghost" size="sm" onClick={() => dismissAll(notifications)}>
							{labels.dismissAll}
						</Button>
					) : undefined
				}
			/>
		</>
	);
};

const SaasNotifications: React.FC<SaasNotificationsProps> = (props) => (
	<C3NotificationProvider>
		<SaasNotificationsContent {...props} />
	</C3NotificationProvider>
);

export {SaasNotifications};
export type {SaasNotificationsLabels, SaasNotificationsProps};
