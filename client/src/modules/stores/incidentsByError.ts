/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {action, makeObservable, observable, override} from 'mobx';

import {
  fetchIncidentsByError,
  IncidentByErrorDto,
} from 'modules/api/incidents/fetchIncidentsByError';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import isEqual from 'lodash/isEqual';

type State = {
  incidents: IncidentByErrorDto[];
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  incidents: [],
  status: 'initial',
};

class IncidentsByError extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  isPollRequestRunning: boolean = false;
  intervalId: null | ReturnType<typeof setInterval> = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      startFetching: action,
      setError: action,
      setIncidents: action,
      reset: override,
    });
  }

  init() {
    if (this.intervalId === null) {
      this.startPolling();
    }
  }

  getIncidentsByError = this.retryOnConnectionLost(async () => {
    this.startFetching();

    try {
      const response = await fetchIncidentsByError();

      if (response.isSuccess) {
        this.setIncidents(response.data);
      } else {
        this.setError();
      }
    } catch {
      this.setError();
    }
  });

  startFetching = () => {
    if (this.state.status === 'initial') {
      this.state.status = 'first-fetch';
    } else {
      this.state.status = 'fetching';
    }
  };

  setError = () => {
    this.state.status = 'error';
  };

  handlePolling = async () => {
    this.isPollRequestRunning = true;
    const response = await fetchIncidentsByError();

    if (this.intervalId !== null) {
      if (response.isSuccess) {
        const incidents = response.data;
        if (!isEqual(incidents, this.state.incidents)) {
          this.setIncidents(incidents);
        }
      } else {
        this.setError();
      }
    }

    this.isPollRequestRunning = false;
  };

  startPolling = async () => {
    this.intervalId = setInterval(() => {
      if (!this.isPollRequestRunning) {
        this.handlePolling();
      }
    }, 5000);
  };

  stopPolling = () => {
    const {intervalId} = this;

    if (intervalId !== null) {
      clearInterval(intervalId);
      this.intervalId = null;
    }
  };

  setIncidents(incidents: IncidentByErrorDto[]) {
    this.state.incidents = incidents;
    this.state.status = 'fetched';
  }

  reset() {
    super.reset();
    this.state = {...DEFAULT_STATE};
    this.stopPolling();
  }
}

export const incidentsByErrorStore = new IncidentsByError();
