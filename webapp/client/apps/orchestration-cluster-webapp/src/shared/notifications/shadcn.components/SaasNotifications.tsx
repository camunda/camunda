/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useMemo, useState} from 'react';
import {Button, NotificationBell, NotificationsPanel, toast, type NavNotification} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';
import {useSaasNotifications} from '#/shared/notifications/saas/useSaasNotifications';
import type {NotificationsConfig} from '#/shared/notifications/saas/api';

const SaasNotifications: React.FC<NotificationsConfig> = (config) => {
	const {t, i18n} = useTranslation();
	const [isOpen, setIsOpen] = useState(false);
	const [unreadAtOpen, setUnreadAtOpen] = useState<Set<string>>(() => new Set());
	const {notifications, isLoading, isError, refetch, markAsRead, dismiss, dismissAll, sendAnalytics} =
		useSaasNotifications(config, () => toast.error(t('headerNotificationsMutationError')));
	const dateFormatter = useMemo(
		() =>
			new Intl.DateTimeFormat(i18n.resolvedLanguage, {
				dateStyle: 'medium',
				timeStyle: 'short',
			}),
		[i18n.resolvedLanguage],
	);
	const unreadNotifications = useMemo(() => notifications.filter(({state}) => state === 'new'), [notifications]);
	const navNotifications = useMemo<NavNotification[]>(
		() =>
			notifications.map((notification) => {
				const description =
					notification.meta?.href !== undefined && notification.meta.label !== undefined ? (
						<>
							<span className="block">{notification.description}</span>
							<a
								className="mt-1 inline-block text-primary-action-default underline underline-offset-2"
								href={notification.meta.href}
								onClick={() => sendAnalytics('notification-clicked-cta', notification.meta?.identifier)}
							>
								{notification.meta.label}
							</a>
						</>
					) : (
						notification.description
					);

				return {
					key: notification.uuid,
					title: notification.title,
					description,
					timestamp: dateFormatter.format(notification.timestamp),
					isRead: notification.state !== 'new' && !unreadAtOpen.has(notification.uuid),
				};
			}),
		[dateFormatter, notifications, sendAnalytics, unreadAtOpen],
	);

	const handleOpenChange = useCallback(
		(isNextOpen: boolean) => {
			setIsOpen(isNextOpen);
			if (!isNextOpen) {
				setUnreadAtOpen(new Set());
				return;
			}

			const unreadIds = unreadNotifications.map(({uuid}) => uuid);
			setUnreadAtOpen(new Set(unreadIds));
			if (unreadIds.length > 0) {
				markAsRead(unreadIds);
			}
			sendAnalytics('notification-panel-opened');
		},
		[markAsRead, sendAnalytics, unreadNotifications],
	);

	const emptyMessage = isLoading
		? t('headerNotificationsLoading')
		: isError
			? t('headerNotificationsError')
			: t('headerNotificationsEmptyDescription');

	return (
		<>
			<NotificationBell
				label={t('headerNotificationsLabel')}
				unreadCount={unreadNotifications.length}
				isActive={isOpen}
				onClick={() => handleOpenChange(!isOpen)}
			/>
			<NotificationsPanel
				title={t('headerNotificationsLabel')}
				notifications={navNotifications}
				open={isOpen}
				onOpenChange={handleOpenChange}
				onDismiss={dismiss}
				emptyMessage={emptyMessage}
				headerAction={
					isError && notifications.length === 0 ? (
						<Button type="button" variant="ghost" size="sm" onClick={() => void refetch()}>
							{t('headerNotificationsRetry')}
						</Button>
					) : notifications.length > 0 ? (
						<Button type="button" variant="ghost" size="sm" onClick={() => dismissAll()}>
							{t('headerNotificationsDismissAll')}
						</Button>
					) : undefined
				}
			/>
		</>
	);
};

export {SaasNotifications};
