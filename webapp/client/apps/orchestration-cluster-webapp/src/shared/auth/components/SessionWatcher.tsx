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

const SessionWatcher: React.FC = observer(() => {
	const location = useLocation();
	const {status} = authenticationStore;
	const removeNotification = useRef<(() => void) | null>(null);
	const {t} = useTranslation();

	const isSessionExpired =
		status === 'logged-out' ||
		status === 'session-expired' ||
		(status === 'session-invalid' && location.pathname !== '/');

	useEffect(() => {
		if (location.pathname === '/login') {
			return;
		}

		if (status === 'session-expired' || status === 'session-invalid') {
			removeNotification.current = notificationsStore.displayNotification({
				kind: 'info',
				title: t('sessionWatcherExpiredTitle'),
				isDismissable: true,
			});
		}
	}, [status, t, location.pathname]);

	useEffect(() => {
		if (status === 'logged-in') {
			removeNotification.current?.();
		}
	}, [status]);

	if (isSessionExpired) {
		return <Navigate to="/login" search={location.href === '/' ? {} : {redirect: location.href}} replace />;
	}

	return null;
});

export {SessionWatcher};
