/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {action, observable, makeObservable} from 'mobx';
import {ToastNotification} from '@carbon/react';

const NOTIFICATION_TIMEOUT = 5000;
const MAX_VISIBLE_NOTIFICATIONS = 5;

type Notification = {
  id: string;
  title: string;
  subtitle?: string;
  date: number;
  isDismissable: boolean;
  kind: NonNullable<React.ComponentProps<typeof ToastNotification>['kind']>;
  hideNotification: () => void;
  isActionable?: boolean;
  actionButtonLabel?: string;
  onActionButtonClick?: () => void;
};

class Notifications {
  notifications: Notification[] = [];
  #notificationsQueue: Notification[] = [];

  constructor() {
    makeObservable(this, {
      displayNotification: action,
      hideNotification: action,
      notifications: observable,
      reset: action,
    });
  }

  displayNotification = (
    notification: Omit<Notification, 'date' | 'id' | 'hideNotification'>,
  ) => {
    const notificationId = `${Date.now()}${Math.random()}`;
    const hideNotification = () => {
      this.hideNotification(notificationId);
    };
    const newNotification: Notification = {
      ...notification,
      date: Date.now(),
      id: notificationId,
      hideNotification,
    };

    if (this.notifications.length >= MAX_VISIBLE_NOTIFICATIONS) {
      this.#enqueueNotification(newNotification);
    } else {
      this.#addAutoRemovalInterval(newNotification);
      this.notifications.unshift(newNotification);
    }

    return newNotification.hideNotification;
  };

  #enqueueNotification = (notification: Notification) => {
    this.#notificationsQueue.unshift(notification);
  };

  #dequeueNotification = () => {
    return this.#notificationsQueue.shift();
  };

  #addAutoRemovalInterval = (notification: Notification) => {
    const delta = 100;
    let intervalId: NodeJS.Timer;
    let time = NOTIFICATION_TIMEOUT;

    intervalId = setInterval(function () {
      if (document.hidden) {
        return;
      }

      time -= delta;

      if (time <= 0) {
        clearInterval(intervalId);
        notification.hideNotification();
      }
    }, delta);
  };

  hideNotification = (notificationId: string) => {
    const queuedNotification = this.#dequeueNotification();

    this.notifications = this.notifications.filter(
      ({id}) => id !== notificationId,
    );

    if (queuedNotification !== undefined) {
      this.#addAutoRemovalInterval(queuedNotification);
      this.notifications.unshift(queuedNotification);
    }
  };

  reset = () => {
    this.notifications = [];
  };
}

const notificationsStore = new Notifications();

export {notificationsStore};
export type {Notification};
