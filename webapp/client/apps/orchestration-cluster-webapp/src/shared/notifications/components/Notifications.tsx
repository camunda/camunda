/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react-lite';
import {TransitionGroup} from 'react-transition-group';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {Notification} from '#/shared/notifications/components/Notification';
import styles from '#/shared/notifications/components/Notifications.module.scss';

const Notifications: React.FC = observer(() => {
	const {notifications} = notificationsStore;

	return (
		<TransitionGroup className={styles['container']!}>
			{notifications.map((notification) => (
				<Notification key={notification.id} notification={notification} />
			))}
		</TransitionGroup>
	);
});

export {Notifications};
