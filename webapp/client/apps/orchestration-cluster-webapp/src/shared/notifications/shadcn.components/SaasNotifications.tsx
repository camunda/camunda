/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {Button, NotificationBell, NotificationsPanel, type NavNotification} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';
import {fetchSaasToken} from '#/shared/saas/fetchSaasToken';
import {Notifications, type Notification, type NotificationsFailure} from '#/shared/notifications/saas';

type Props = {
	organizationId: string;
	url: string;
};

async function getAccessToken(): Promise<string> {
	const token = await fetchSaasToken();
	if (token.length === 0) {
		throw new Error('SaaS access token is unavailable');
	}
	return token;
}

const SaasNotifications: React.FC<Props> = ({organizationId, url}) => {
	const {t, i18n} = useTranslation();
	const service = useMemo(() => new Notifications({organizationId, url, getAccessToken}), [organizationId, url]);
	const [notifications, setNotifications] = useState<readonly Notification[]>([]);
	const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');
	const [isOpen, setIsOpen] = useState(false);
	const [unreadAtOpen, setUnreadAtOpen] = useState<Set<string>>(() => new Set());
	const notificationsRef = useRef(notifications);
	const isOpenRef = useRef(isOpen);

	useEffect(() => {
		const unsubscribeConnect = service.on('connect', (items) => {
			setNotifications(items);
			setStatus('ready');
		});
		const unsubscribeChange = service.on('change', setNotifications);
		const unsubscribeError = service.on('error', ({operation}: NotificationsFailure) => {
			if (operation === 'connect' && notificationsRef.current.length === 0) {
				setStatus('error');
			}
		});
		service.connect();

		return () => {
			if (isOpenRef.current && notificationsRef.current.some(({state}) => state === 'new')) {
				service.send('readAll');
			}
			unsubscribeConnect();
			unsubscribeChange();
			unsubscribeError();
			service.disconnect();
		};
	}, [service]);

	useEffect(() => {
		notificationsRef.current = notifications;
		isOpenRef.current = isOpen;
	});

	const dateFormatter = useMemo(
		() =>
			new Intl.DateTimeFormat(i18n.resolvedLanguage, {
				dateStyle: 'medium',
				timeStyle: 'short',
			}),
		[i18n.resolvedLanguage],
	);
	const navNotifications = useMemo<NavNotification[]>(
		() =>
			notifications.map((notification) => ({
				key: notification.uuid,
				title: notification.title,
				description: notification.description,
				timestamp: dateFormatter.format(notification.timestamp),
				isRead: notification.state !== 'new' && !unreadAtOpen.has(notification.uuid),
				href: notification.meta?.href,
			})),
		[dateFormatter, notifications, unreadAtOpen],
	);
	const unreadNotifications = useMemo(() => notifications.filter(({state}) => state === 'new'), [notifications]);

	const handleOpenChange = useCallback(
		(isNextOpen: boolean) => {
			setIsOpen(isNextOpen);
			if (!isNextOpen) {
				if (unreadNotifications.length > 0) {
					service.send('readAll');
				}
				setUnreadAtOpen(new Set());
				return;
			}

			setUnreadAtOpen(new Set(unreadNotifications.map(({uuid}) => uuid)));
			service.send('readAll');
		},
		[service, unreadNotifications],
	);

	const emptyMessage =
		status === 'loading'
			? t('headerNotificationsLoading')
			: status === 'error'
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
				onDismiss={(key) => service.send('dismiss', key)}
				emptyMessage={emptyMessage}
				headerAction={
					status === 'error' && notifications.length === 0 ? (
						<Button type="button" variant="ghost" size="sm" onClick={() => service.refresh()}>
							{t('headerNotificationsRetry')}
						</Button>
					) : notifications.length > 0 ? (
						<Button type="button" variant="ghost" size="sm" onClick={() => service.send('dismissAll')}>
							{t('headerNotificationsDismissAll')}
						</Button>
					) : undefined
				}
			/>
		</>
	);
};

export {SaasNotifications};
