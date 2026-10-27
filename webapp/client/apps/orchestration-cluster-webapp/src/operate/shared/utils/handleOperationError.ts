/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {t} from 'i18next';
import {notificationsStore} from '#/shared/notifications/notifications.store';

function handleOperationError(statusCode?: number) {
	notificationsStore.displayNotification(
		statusCode === 403
			? {
					kind: 'warning',
					title: t('operate.shared.operations.forbiddenTitle'),
					subtitle: t('operate.shared.operations.forbiddenSubtitle'),
					isDismissable: true,
				}
			: {
					kind: 'error',
					title: t('operate.shared.operations.creationErrorTitle'),
					isDismissable: true,
				},
	);
}

export {handleOperationError};
