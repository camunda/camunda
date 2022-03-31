/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React, {useEffect, useState} from 'react';
import {authenticationStore} from 'modules/stores/authentication';
import {observer} from 'mobx-react-lite';
import {useNotifications, Notification} from 'modules/notifications';
import {useLocation} from 'react-router-dom';
import {Pages} from 'modules/constants/pages';

const SessionWatcher: React.FC = observer(() => {
  const [notification, setNotification] = useState<Notification | undefined>();

  const {displayNotification} = useNotifications();
  const status = authenticationStore.status;
  const location = useLocation();

  useEffect(() => {
    async function handleSessionExpiration() {
      setNotification(
        await displayNotification('info', {
          headline: 'Session expired',
        }),
      );
    }

    if (notification === undefined && location.pathname !== Pages.Login) {
      if (
        status === 'session-expired' ||
        (status === 'session-invalid' && location.pathname !== Pages.Initial())
      ) {
        handleSessionExpiration();
      }
    }

    if (status === 'logged-in') {
      notification?.remove();
      setNotification(undefined);
    }
  }, [status, notification, displayNotification, location.pathname]);

  return null;
});

export {SessionWatcher};
