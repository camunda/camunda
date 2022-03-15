/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import mixpanel, {Mixpanel} from 'mixpanel-browser';
import {getStage} from './getStage';

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
const STAGE_ENV = getStage(window.location.host);

mixpanel.init(process.env.REACT_APP_MIXPANEL_TOKEN, {
  opt_out_tracking_by_default: true,
  api_host: process.env.REACT_MIXPANEL_HOST,
});

class Tracking {
  #mixpanel: null | Mixpanel = window.clientConfig?.organizationId
    ? mixpanel
    : null;

  constructor() {
    this.#mixpanel?.register({
      organizationId: window.clientConfig?.organizationId,
      clusterId: window.clientConfig?.clusterId,
      stage: STAGE_ENV,
      version: process.env.REACT_APP_VERSION,
    });
  }

  track(events: Events) {
    const {eventName, ...properties} = events;

    try {
      this.#mixpanel?.track(`${EVENT_PREFIX}${eventName}`, properties);
    } catch (error) {
      console.error(`Can't track event: ${eventName}`, error);
    }
  }

  loadPermissions(): Promise<void> {
    if (
      process.env.NODE_ENV === 'development' ||
      !['prod', 'int'].includes(STAGE_ENV) ||
      this.#mixpanel === null
    ) {
      return Promise.resolve();
    }

    return new Promise((resolve) => {
      const osanoScriptElement = document.createElement('script');

      if (STAGE_ENV === 'int') {
        osanoScriptElement.src = process.env.REACT_APP_OSANO_INT_ENV_URL;
      }

      if (STAGE_ENV === 'prod') {
        osanoScriptElement.src = process.env.REACT_APP_OSANO_PROD_ENV_URL;
      }

      document.head.appendChild(osanoScriptElement);

      setTimeout(resolve, 1000);

      osanoScriptElement.onload = () => {
        if (window.Osano?.cm?.analytics) {
          this.#mixpanel?.opt_in_tracking();
        }
        resolve();
      };
      osanoScriptElement.onerror = () => {
        resolve();
      };
    });
  }
}

const tracking = new Tracking();

export {tracking};
