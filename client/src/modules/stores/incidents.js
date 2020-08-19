/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, autorun, computed} from 'mobx';
import {fetchWorkflowInstanceIncidents} from 'modules/api/instances';
import {currentInstance} from 'modules/stores/currentInstance';
import {singleInstanceDiagram} from 'modules/stores/singleInstanceDiagram';
import {addFlowNodeName, mapify} from './mappers';

const DEFAULT_STATE = {
  response: null,
  isLoaded: false,
  isFailed: false,
};

class Incidents {
  state = {...DEFAULT_STATE};
  intervalId = null;
  disposer = null;

  init() {
    this.disposer = autorun(() => {
      if (currentInstance.state.instance?.state === 'INCIDENT') {
        if (this.intervalId === null) {
          this.fetchIncidents(currentInstance.state.instance.id);
          this.startPolling(currentInstance.state.instance.id);
        }
      } else {
        this.stopPolling();
      }
    });
  }

  startPolling = async (id) => {
    this.intervalId = setInterval(() => {
      this.handlePolling(id);
    }, 5000);
  };

  fetchIncidents = async (id) => {
    const response = await fetchWorkflowInstanceIncidents(id);
    this.setIncidents(response);
  };

  stopPolling = () => {
    clearInterval(this.intervalId);
  };

  handlePolling = async (id) => {
    const response = await fetchWorkflowInstanceIncidents(id);

    if (this.intervalId !== null) {
      this.setIncidents(response);
    }
  };

  setError() {
    this.state.isLoaded = true;
    this.state.isFailed = true;
  }

  setIncidents = (response) => {
    this.state.response = response;
    this.state.isLoaded = true;
    this.state.isFailed = false;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
    this.stopPolling();
    this.intervalId = null;
    if (this.disposer !== null) {
      this.disposer();
    }
  };

  get incidents() {
    if (this.state.response === null) return [];
    return this.state.response.incidents.map((incident) => {
      const metaData = singleInstanceDiagram.getMetaData(incident.flowNodeId);
      return addFlowNodeName(incident, metaData);
    });
  }

  get flowNodes() {
    return this.state.response !== null
      ? mapify(
          this.state.response.flowNodes.map((flowNode) => {
            const metaData = singleInstanceDiagram.getMetaData(
              flowNode.flowNodeId
            );
            return addFlowNodeName(flowNode, metaData);
          }),
          'flowNodeId'
        )
      : new Map();
  }

  get errorTypes() {
    return this.state.response !== null
      ? mapify(this.state.response.errorTypes, 'errorType')
      : new Map();
  }

  get incidentsCount() {
    return this.state.response !== null ? this.state.response.count : 0;
  }
}

decorate(Incidents, {
  state: observable,
  setError: action,
  setIncidents: action,
  reset: action,
  incidents: computed,
  flowNodes: computed,
  errorTypes: computed,
  incidentsCount: computed,
});

export const incidents = new Incidents();
