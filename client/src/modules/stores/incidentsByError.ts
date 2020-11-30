/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {makeAutoObservable} from 'mobx';

import {fetchIncidentsByError} from 'modules/api/incidents';

type Workflow = {
  workflowId: string;
  version: number;
  name: null | string;
  bpmnProcessId: string;
  errorMessage: string;
  instancesWithActiveIncidentsCount: number;
  activeInstancesCount: number;
};
type IncidentByError = {
  errorMessage: string;
  instancesWithErrorCount: number;
  workflows: Workflow[];
};

type State = {
  incidents: IncidentByError[];
  status: 'initial' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  incidents: [],
  status: 'initial',
};

class IncidentsByError {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  getIncidentsByError = async () => {
    this.startFetching();

    try {
      const response = await fetchIncidentsByError();

      if (response.ok) {
        this.setIncidents(await response.json());
      } else {
        this.setError();
      }
    } catch {
      this.setError();
    }
  };

  startFetching = () => {
    this.state.status = 'fetching';
  };

  setError = () => {
    this.state.status = 'error';
  };

  setIncidents(incidents: IncidentByError[]) {
    this.state.incidents = incidents;
    this.state.status = 'fetched';
  }

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const incidentsByErrorStore = new IncidentsByError();
