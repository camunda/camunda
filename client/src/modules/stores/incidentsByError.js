/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, computed} from 'mobx';

import {fetchIncidentsByError} from 'modules/api/incidents';

const DEFAULT_STATE = {
  incidents: [],
  isLoaded: false,
  isFailed: false,
};

class IncidentsByError {
  state = {...DEFAULT_STATE};

  getIncidentsByError = async () => {
    const {data} = await fetchIncidentsByError();

    if (data.error) {
      this.setError();
    } else {
      this.setIncidents(data);
    }
  };

  setError() {
    this.state.isLoaded = true;
    this.state.isFailed = true;
  }

  setIncidents(incidents) {
    this.state.incidents = incidents;
    this.state.isLoaded = true;
    this.state.isFailed = false;
  }

  get isDataAvailable() {
    const {incidents, isLoaded, isFailed} = this.state;
    return !isFailed && isLoaded && incidents.length > 0;
  }

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

decorate(IncidentsByError, {
  state: observable,
  setError: action,
  setIncidents: action,
  reset: action,
  isDataAvailable: computed,
});

export const incidentsByErrorStore = new IncidentsByError();
