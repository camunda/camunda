/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Mixpanel} from 'mixpanel-browser';
import {getStage} from './getStage';

const MIXPANEL_PUBLIC_TOKEN = '1104cabe553c23b7e67d56b1976437aa';
const EVENT_PREFIX = 'operate:';
type Events =
  | {
      eventName: 'navigation';
      link:
        | 'header-logo'
        | 'header-dashboard'
        | 'header-instances'
        | 'dashboard-running-instances'
        | 'dashboard-instances-with-incidents'
        | 'dashboard-active-instances'
        | 'instance-called-instances';
    }
  | {
      eventName: 'navigation';
      link: 'dashboard-instances-by-process-all-versions';
      process: string;
    }
  | {
      eventName: 'navigation';
      link: 'dashboard-instances-by-process-single-version';
      version: string;
      process: string;
    }
  | {
      eventName: 'navigation';
      link: 'dashboard-incidents-by-error-all-processess';
      errorMessage: string;
    }
  | {
      eventName: 'navigation';
      link: 'dashboard-incidents-by-error-single-process';
      errorMessage: string;
      process: string;
      version: string;
    }
  | {
      eventName: 'navigation';
      link:
        | 'instances-instance-details'
        | 'instances-parent-instance-details'
        | 'instance-parent-details'
        | 'instance-breadcrumb';
      instanceId: string;
    }
  | {
      eventName: 'theme-toggle';
      toggledTo: 'light' | 'dark';
    }
  | {
      eventName: 'batch-operation';
      operationType: OperationEntityType;
    }
  | {
      eventName: 'single-operation';
      operationType: OperationEntityType;
    }
  | {
      eventName: 'instances-loaded';
      filters: string[];
      sortBy?: string;
      sortOrder?: 'desc' | 'asc';
    }
  | {eventName: 'incident-bar-opened'}
  | {eventName: 'incident-bar-closed'}
  | {eventName: 'instance-toggle-end-time'}
  | {eventName: 'instance-history-log-selection-toggle'}
  | {eventName: 'instance-diagram-selection-toggle'};

class Tracking {
  #mixpanel: null | Mixpanel = null;
  constructor() {
    if (window.clientConfig?.organizationId) {
      import('mixpanel-browser').then((mixpanel) => {
        mixpanel.init(MIXPANEL_PUBLIC_TOKEN);

        this.#mixpanel = mixpanel;
      });
    }
  }

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
