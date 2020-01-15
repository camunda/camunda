/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import React from 'react';
import update from 'immutability-helper';
import {getRandomId} from 'services';

import Notification from './Notification';

import './Notifications.scss';
import {t} from 'translation';

let notificationsInstance;

export default class Notifications extends React.Component {
  constructor(props) {
    super(props);

    notificationsInstance = this;

    this.state = {
      notifications: []
    };
  }

  addNotification = config => {
    this.setState(state => update(state, {notifications: {$push: [config]}}));
  };

  removeNotification = notificationToDelete => {
    this.setState(state => ({
      notifications: state.notifications.filter(
        notification => notification !== notificationToDelete
      )
    }));
  };

  render() {
    return (
      <div className="Notifications">
        {this.state.notifications.map(config => (
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
  let text = error;

  if (typeof error.json === 'function') {
    try {
      const {errorCode, errorMessage} = await error.json();
      text = errorCode ? t('apiErrors.' + errorCode) : errorMessage;
    } catch (e) {
      // We should show an error, but cannot parse the error
      // e.g. the server did not return the expected error object
      console.error('Tried to parse error object, but failed', error);
      return;
    }
  } else if (error.message) {
    text = error.message;
  }

  addNotification({type: 'error', text});
}
