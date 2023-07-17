/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useEffect, useState} from 'react';
import {Notification, useNotifications} from 'modules/notifications';

const NetworkStatusWatcher: React.FC = () => {
  const {displayNotification} = useNotifications();
  const [notification, setNotification] = useState<Notification | undefined>();

  useEffect(() => {
    async function handleDisconnection() {
      setNotification(
        await displayNotification('info', {
          headline: 'Internet connection lost',
          isDismissable: false,
        }),
      );
    }

    if (!window.navigator.onLine) {
      handleDisconnection();
    }

    window.addEventListener('offline', handleDisconnection);

    return () => {
      window.removeEventListener('offline', handleDisconnection);
    };
  }, [displayNotification]);

  useEffect(() => {
    function handleReconnection() {
      notification?.remove();
    }

    window.addEventListener('online', handleReconnection);

    return () => {
      window.removeEventListener('online', handleReconnection);
    };
  }, [notification]);

  return null;
};

export {NetworkStatusWatcher};
