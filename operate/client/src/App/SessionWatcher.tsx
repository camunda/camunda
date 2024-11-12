/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {observer} from 'mobx-react-lite';
import {useLocation} from 'react-router-dom';
import {Paths} from 'modules/Routes';
import {authenticationStore} from 'modules/stores/authentication';
import {notificationsStore} from 'modules/stores/notifications';

const SessionWatcher: React.FC = observer(() => {
  const removeNotification = useRef<(() => void) | null>(null);
  const {status} = authenticationStore.state;
  const location = useLocation();

  useEffect(() => {
    async function handleSessionExpiration() {
      removeNotification.current = notificationsStore.displayNotification({
        kind: 'info',
        title: 'Session expired',
        isDismissable: true,
      });
    }

    if (
      removeNotification.current === null &&
      location.pathname !== Paths.login() &&
      (status === 'session-expired' ||
        (location.pathname !== Paths.dashboard() &&
          status === 'invalid-initial-session'))
    ) {
      handleSessionExpiration();
    }

    if (['logged-in', 'user-information-fetched'].includes(status)) {
      removeNotification.current?.();
    }
  }, [status, location.pathname]);

  return null;
});

export {SessionWatcher};
