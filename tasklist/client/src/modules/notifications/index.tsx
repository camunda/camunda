/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
