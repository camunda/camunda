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
import {Alert, Button} from '@camunda/design-system';
import {requestPermission} from '#/shared/os-notifications/requestPermission';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {featureFlags} from '#/shared/feature-flags';
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

	const handleDismiss = () => {
		setIsEnabled(false);
		storeStateLocally('tasklist.areNativeNotificationsEnabled', false);
	};

	const handleActionClick = async () => {
		const result = await requestPermission();
		if (result !== 'default') {
			setIsEnabled(false);
		}
	};

	if (featureFlags.dsTasklistUI) {
		return (
			<div className={styles.alertContainerDS}>
				<Alert
					variant="info"
					title={t('tasklist.turnOnNotificationTitle')}
					description={t('tasklist.turnOnNotificationSubtitle')}
					dismissible
					onDismiss={handleDismiss}
				>
					<Button variant="ghost" size="sm" onClick={handleActionClick}>
						{t('tasklist.turnOnNotificationsActionButton')}
					</Button>
				</Alert>
			</div>
		);
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
				onActionButtonClick={handleActionClick}
				onClose={handleDismiss}
				className={styles.actionableNotification}
				lowContrast
			/>
		</div>
	);
};

export {TurnOnNotificationPermission};
