/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import update from 'immutability-helper';
import {getRandomId} from 'services';

import Notification from './Notification';

import './Notifications.scss';

let notificationsInstance;

export default class Notifications extends React.Component {
  constructor(props) {
    super(props);

    notificationsInstance = this;

    this.state = {
      notifications: [],
    };
  }

  addNotification = (config) => {
    this.setState((state) => update(state, {notifications: {$push: [config]}}));
  };

  removeNotification = (notificationToDelete) => {
    this.setState((state) => ({
      notifications: state.notifications.filter(
        (notification) => notification !== notificationToDelete
      ),
    }));
  };

  render() {
    return (
      <div className="Notifications">
        {this.state.notifications.map((config) => (
          <Notification
            config={config}
            remove={() => this.removeNotification(config)}
            key={config.id}
          />
        ))}
      </div>
    );
  }
}

export function addNotification(config) {
  if (typeof config === 'string') {
    config = {text: config};
  }
  notificationsInstance.addNotification({...config, id: getRandomId()});
}

export async function showError(error) {
  const text = typeof error === 'string' ? error : error.message;

  addNotification({type: 'error', text});
}
