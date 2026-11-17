/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {useTranslation} from 'react-i18next';
import {toast} from '@camunda/design-system';

const NetworkStatusWatcher: React.FC = () => {
	const notificationId = useRef<string | number | null>(null);
	const {t} = useTranslation();

	useEffect(() => {
		function handleDisconnection() {
			if (notificationId.current !== null) {
				return;
			}

			notificationId.current = toast.info(t('networkStatusOfflineTitle'), {
				duration: Infinity,
			});
		}

		function handleReconnection() {
			if (notificationId.current !== null) {
				toast.dismiss(notificationId.current);
				notificationId.current = null;
			}
		}

		if (!window.navigator.onLine) {
			handleDisconnection();
		}

		window.addEventListener('offline', handleDisconnection);
		window.addEventListener('online', handleReconnection);

		return () => {
			window.removeEventListener('offline', handleDisconnection);
			window.removeEventListener('online', handleReconnection);
			handleReconnection();
		};
	}, [t]);

	return null;
};

export {NetworkStatusWatcher};
