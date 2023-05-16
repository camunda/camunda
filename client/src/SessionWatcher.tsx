/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useEffect, useRef} from 'react';
import {authenticationStore} from 'modules/stores/authentication';
import {observer} from 'mobx-react-lite';
import {useLocation} from 'react-router-dom';
import {pages} from 'modules/routing';
import {notificationsStore} from 'modules/stores/notifications';

const SessionWatcher: React.FC = observer(() => {
  const removeNotification = useRef<(() => void) | null>(null);
  const status = authenticationStore.status;
  const location = useLocation();

  useEffect(() => {
    function handleSessionExpiration() {
      removeNotification.current = notificationsStore.displayNotification({
        kind: 'info',
        title: 'Session expired',
        isDismissable: true,
      });
    }

    if (location.pathname === pages.login) {
      return;
    }

    if (
      status === 'session-expired' ||
      (status === 'session-invalid' && location.pathname !== pages.initial)
    ) {
      handleSessionExpiration();
    }
  }, [status, location.pathname]);

  useEffect(() => {
    if (status === 'logged-in') {
      removeNotification.current?.();
    }
  }, [status]);

  return null;
});

export {SessionWatcher};
