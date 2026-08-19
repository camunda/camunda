/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {toast} from '@camunda/design-system';
import {Navigate, useLocation} from '@tanstack/react-router';
import {observer} from 'mobx-react-lite';
import {useEffect, useRef} from 'react';
import {useTranslation} from 'react-i18next';
import {authenticationStore} from '#/shared/auth/authentication.store';

const SessionWatcher: React.FC = observer(() => {
	const location = useLocation();
	const {status} = authenticationStore;
	const toastId = useRef<string | number | null>(null);
	const {t} = useTranslation();
	const isSessionExpired =
		status === 'logged-out' ||
		status === 'session-expired' ||
		(status === 'session-invalid' && location.pathname !== '/shadcn');

	useEffect(() => {
		if (location.pathname.endsWith('/login')) {
			return;
		}

		if (status === 'session-expired' || status === 'session-invalid') {
			toastId.current = toast.info(t('sessionWatcherExpiredTitle'));
		}
	}, [location.pathname, status, t]);

	useEffect(() => {
		if (status === 'logged-in' && toastId.current !== null) {
			toast.dismiss(toastId.current);
			toastId.current = null;
		}
	}, [status]);

	if (isSessionExpired) {
		return (
			<Navigate
				to="/shadcn/tasklist/login"
				search={location.href === '/shadcn/tasklist' ? {} : {redirect: location.href}}
				replace
			/>
		);
	}

	return null;
});

export {SessionWatcher};
