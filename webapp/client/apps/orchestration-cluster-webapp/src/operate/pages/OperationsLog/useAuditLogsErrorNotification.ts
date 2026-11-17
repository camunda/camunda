/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {useTranslation} from 'react-i18next';
import {logger} from '#/operate/shared/utils/logger';
import {notificationsStore} from '#/shared/notifications/notifications.store';

function useAuditLogsErrorNotification(error: Error | null, dataUpdatedAt: number) {
	const {t} = useTranslation();
	const isFetchErrorReported = useRef(false);
	useEffect(() => {
		isFetchErrorReported.current = false;
	}, [dataUpdatedAt]);
	useEffect(() => {
		if (!error) {
			return;
		}
		logger.error(error);
		if (isFetchErrorReported.current) {
			return;
		}
		isFetchErrorReported.current = true;
		notificationsStore.displayNotification({
			isDismissable: true,
			kind: 'error',
			title: t('operate.operationsLog.notifications.fetchFailed'),
		});
	}, [error, t]);
}

export {useAuditLogsErrorNotification};
