/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react-lite';
import {useRouter, useLocation} from '@tanstack/react-router';
import {authenticationStore} from '#/modules/auth/authentication.store';

const INITIAL_PATH = '/';

const SessionWatcher: React.FC = observer(() => {
	const router = useRouter();
	const location = useLocation();
	const {status} = authenticationStore;

	const isSessionExpired =
		status === 'session-expired' || (status === 'session-invalid' && location.pathname !== INITIAL_PATH);

	useEffect(() => {
		if (isSessionExpired) {
			void router.navigate({
				to: '/login',
				search: location.href === '/' ? {} : {redirect: location.href},
				replace: true,
			});
		}
	}, [isSessionExpired, location.href, router]);

	// TODO: show a session-expired notification once a notifications module is available

	return null;
});

export {SessionWatcher};
