/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ActionableNotification} from '@carbon/react';
import {useState} from 'react';
import {useTranslation} from 'react-i18next';
import {requestPermission} from '#/shared/os-notifications/requestPermission';
import {tracking} from '#/shared/tracking';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import styles from './TurnOnNotificationPermission.module.scss';

const TurnOnNotificationPermission: React.FC = () => {
	const {t} = useTranslation();
	const areNativeNotificationsEnabled = getStateLocally('tasklist.areNativeNotificationsEnabled');
	const [isEnabled, setIsEnabled] = useState(
		'Notification' in window && Notification.permission === 'default' && !(areNativeNotificationsEnabled === false),
	);

	if (!isEnabled) {
		return null;
	}

	return (
		<div>
			<ActionableNotification
				inline
				kind="info"
				role="status"
				aria-live="polite"
				title={t('tasklist.turnOnNotificationTitle')}
				subtitle={t('tasklist.turnOnNotificationSubtitle')}
				actionButtonLabel={t('tasklist.turnOnNotificationsActionButton')}
				onActionButtonClick={async () => {
					const result = await requestPermission();
					if (result !== undefined) {
						tracking.track({
							eventName: 'tasklist:os-notification-permission-requested',
							outcome: result,
						});
					}
					if (result !== 'default') {
						setIsEnabled(false);
					}
				}}
				onClose={() => {
					setIsEnabled(false);
					storeStateLocally('tasklist.areNativeNotificationsEnabled', false);
				}}
				className={styles.actionableNotification}
				lowContrast
			/>
		</div>
	);
};

export {TurnOnNotificationPermission};
