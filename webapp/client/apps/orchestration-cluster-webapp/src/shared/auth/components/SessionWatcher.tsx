/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react-lite';
import {Navigate, useLocation} from '@tanstack/react-router';
import {useEffect, useRef} from 'react';
import {useTranslation} from 'react-i18next';
import {authenticationStore} from '#/shared/auth/authentication.store';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {isTasklistPath} from '#/shared/auth/isTasklistPath';

const SessionWatcher: React.FC = observer(() => {
	const location = useLocation();
	const {status} = authenticationStore;
	const removeNotification = useRef<(() => void) | null>(null);
	const {t} = useTranslation();
	const isTasklistIndex = location.href === '/tasklist';

	const isSessionExpired =
		status === 'logged-out' ||
		status === 'session-expired' ||
		(status === 'session-invalid' && location.pathname !== '/');

	useEffect(() => {
		if (location.pathname.endsWith('/login')) {
			return;
		}

		if (status === 'session-expired' || status === 'session-invalid') {
			removeNotification.current = notificationsStore.displayNotification({
				kind: 'info',
				title: t('sessionWatcherExpiredTitle'),
				isDismissable: true,
			});
		}
	}, [location.pathname, status, t]);

	useEffect(() => {
		if (status === 'logged-in') {
			removeNotification.current?.();
		}
	}, [status]);

	if (isSessionExpired) {
		return (
			<Navigate
				to={isTasklistPath(location.pathname) ? '/tasklist/login' : '/login'}
				search={location.href === '/' || isTasklistIndex ? {} : {redirect: location.href}}
				replace
			/>
		);
	}

	return null;
});

export {SessionWatcher};
