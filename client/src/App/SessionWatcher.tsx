/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useState} from 'react';
import {observer} from 'mobx-react-lite';
import {useNotifications, Notification} from 'modules/notifications';
import {useLocation} from 'react-router-dom';
import {Paths} from 'modules/routes';
import {authenticationStore} from 'modules/stores/authentication';

const SessionWatcher: React.FC = observer(() => {
  const [notification, setNotification] = useState<Notification | undefined>();
  const {displayNotification} = useNotifications();
  const {status} = authenticationStore.state;
  const location = useLocation();

  useEffect(() => {
    async function handleSessionExpiration() {
      setNotification(
        await displayNotification('info', {
          headline: 'Session expired',
        })
      );
    }

    if (
      notification === undefined &&
      location.pathname !== Paths.login() &&
      (status === 'session-expired' ||
        (location.pathname !== Paths.dashboard() &&
          status === 'invalid-initial-session'))
    ) {
      handleSessionExpiration();
    }

    if (['logged-in', 'user-information-fetched'].includes(status)) {
      notification?.remove();
      setNotification(undefined);
    }
  }, [status, notification, displayNotification, location.pathname]);

  return null;
});

export {SessionWatcher};
