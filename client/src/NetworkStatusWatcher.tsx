/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {notificationsStore} from 'modules/stores/notifications';
import {useEffect, useRef} from 'react';

const NetworkStatusWatcher: React.FC = () => {
  const removeNotification = useRef<(() => void) | null>(null);

  useEffect(() => {
    async function handleDisconnection() {
      removeNotification.current = notificationsStore.displayNotification({
        kind: 'info',
        title: 'Internet connection lost',
        isDismissable: false,
      });
    }

    function handleReconnection() {
      removeNotification.current?.();
    }

    if (!window.navigator.onLine) {
      handleDisconnection();
    }

    window.addEventListener('offline', handleDisconnection);
    window.addEventListener('online', handleReconnection);

    return () => {
      window.removeEventListener('offline', handleDisconnection);
      window.removeEventListener('online', handleReconnection);
    };
  }, []);

  return null;
};

export {NetworkStatusWatcher};
