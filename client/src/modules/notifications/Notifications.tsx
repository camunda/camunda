/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useState} from 'react';
import update from 'immutability-helper';

import {getRandomId} from 'services';
import {ErrorResponse} from 'request';

import Notification, {Config} from './Notification';

import './Notifications.scss';

interface NotificationInstance {
  addNotification: (notification: Config) => void;
  removeNotification: (notification: Config) => void;
}

let notificationsInstance: NotificationInstance;

export default function Notifications(): JSX.Element {
  const [notifications, setNotifications] = useState<Config[]>([]);

  notificationsInstance = {
    addNotification: (config) => {
      setNotifications((notifications) => update(notifications, {$push: [config]}));
    },
    removeNotification: (notificationToDelete) => {
      setNotifications((notifications) =>
        notifications.filter((notification) => notification !== notificationToDelete)
      );
    },
  };

  return (
    <div className="Notifications">
      {notifications.map((config) => (
        <Notification
          config={config}
          remove={() => notificationsInstance?.removeNotification(config)}
          key={config.id}
        />
      ))}
    </div>
  );
}

export function addNotification(config: Config | string) {
  if (typeof config === 'string') {
    config = {text: config};
  }
  notificationsInstance?.addNotification({...config, id: getRandomId()});
}

export async function showError(error: string | ErrorResponse) {
  const text = typeof error === 'string' ? error : error.message;

  addNotification({type: 'error', text});
}
