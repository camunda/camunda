/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect} from 'react';
import {observer} from 'mobx-react-lite';
import {useNavigate} from '@tanstack/react-router';
import {authenticationStore} from '#/modules/auth/stores/authentication';

const SessionWatcher: React.FC = observer(() => {
	const navigate = useNavigate();
	const {status} = authenticationStore;

	useEffect(() => {
		if (status === 'session-expired' || status === 'session-invalid') {
			// TODO: display a session expired notification once a notification store is available
			navigate({to: '/login', replace: true});
		}
	}, [status, navigate]);

	return null;
});

export {SessionWatcher};
