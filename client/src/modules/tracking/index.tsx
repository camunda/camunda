/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import mixpanel, {Mixpanel} from 'mixpanel-browser';
import {getStage} from './getStage';

const MIXPANEL_PUBLIC_TOKEN = '1104cabe553c23b7e67d56b1976437aa';
const EVENT_PREFIX = 'tasklist:';
type Events =
  | {
      eventName: 'task-opened' | 'task-unclaimed' | 'task-claimed';
    }
  | {
      eventName: 'task-completed';
      isCamundaForm: boolean;
    }
  | {eventName: 'tasks-filtered'; filter: string};

mixpanel.init(MIXPANEL_PUBLIC_TOKEN);

class Tracking {
  #mixpanel: null | Mixpanel =
    window.clientConfig?.mixpanelActivated &&
    window.clientConfig?.organizationId
      ? mixpanel
      : null;

  track(events: Events) {
    const {eventName, ...properties} = events;

    try {
      this.#mixpanel?.track(`${EVENT_PREFIX}${eventName}`, {
        ...properties,
        organizationId: window.clientConfig?.organizationId,
        clusterId: window.clientConfig?.clusterId,
        stage: getStage(window.location.host),
        version: process.env.REACT_APP_VERSION,
      });
    } catch (error) {
      console.error(`Can't track event: ${eventName}`, error);
    }
  }
}

const tracking = new Tracking();

export {tracking};
