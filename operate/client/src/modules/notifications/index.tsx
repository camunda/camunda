/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {observer} from 'mobx-react-lite';
import {notificationsStore} from 'modules/stores/notifications';
import {Container} from './NotificationContainer';
import {TransitionGroup} from './TransitionGroup';
import {Notification} from './Notification';

const TRANSITION_DURATION = 300;

const Notifications: React.FC = observer(() => {
  const {notifications} = notificationsStore;

  return (
    <Container>
      <TransitionGroup $animationTimeout={TRANSITION_DURATION}>
        {notifications.map((notification) => (
          <Notification
            key={notification.id}
            notification={notification}
            animationTimeout={TRANSITION_DURATION}
          />
        ))}
      </TransitionGroup>
    </Container>
  );
});

export {Notifications};
