/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef, useState} from 'react';
import {observer} from 'mobx-react-lite';
import {useNotifications, Notification} from 'modules/notifications';
import {useLocation} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {authenticationStore} from 'modules/stores/authentication';
import {notificationsStore} from 'modules/stores/carbonNotifications';
import {LegacyPaths} from 'modules/legacyRoutes';

const SessionWatcher: React.FC = observer(() => {
  const [notification, setNotification] = useState<Notification | undefined>();
  const removeNotification = useRef<(() => void) | null>(null);
  const {displayNotification} = useNotifications();
  const {status} = authenticationStore.state;
  const location = useLocation();

  useEffect(() => {
    async function handleSessionExpiration() {
      if (location.pathname.includes('legacy')) {
        setNotification(
          await displayNotification('info', {
            headline: 'Session expired',
          }),
        );
      } else {
        removeNotification.current = notificationsStore.displayNotification({
          kind: 'info',
          title: 'Session expired',
          isDismissable: true,
        });
      }
    }

    if (location.pathname.includes('legacy')) {
      if (
        notification === undefined &&
        location.pathname !== LegacyPaths.login() &&
        (status === 'session-expired' ||
          (location.pathname !== LegacyPaths.dashboard() &&
            status === 'invalid-initial-session'))
      ) {
        handleSessionExpiration();
      }
    } else {
      if (
        notification === undefined &&
        location.pathname !== Paths.login() &&
        (status === 'session-expired' ||
          (location.pathname !== Paths.dashboard() &&
            status === 'invalid-initial-session'))
      ) {
        handleSessionExpiration();
      }
    }

    if (['logged-in', 'user-information-fetched'].includes(status)) {
      if (location.pathname.includes('legacy')) {
        notification?.remove();
        setNotification(undefined);
      } else {
        removeNotification.current?.();
      }
    }
  }, [status, notification, displayNotification, location.pathname]);

  return null;
});

export {SessionWatcher};
