/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  makeObservable,
  computed,
  action,
  observable,
  autorun,
  override,
  type IReactionDisposer,
} from 'mobx';
import {
  fetchProcessInstanceIncidents,
  type ProcessInstanceIncidentsDto,
  type IncidentDto,
} from 'modules/api/processInstances/fetchProcessInstanceIncidents';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {tracking} from 'modules/tracking';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import type {ProcessInstanceEntity} from 'modules/types/operate';

type Incident = IncidentDto & {isSelected: boolean; flowNodeName: string};

type State = {
  response: null | ProcessInstanceIncidentsDto;
  isLoaded: boolean;
  selectedErrorTypes: string[];
  selectedFlowNodes: string[];
  isIncidentBarOpen: boolean;
};

const DEFAULT_STATE: State = {
  response: null,
  isLoaded: false,
  selectedErrorTypes: [],
  selectedFlowNodes: [],
  isIncidentBarOpen: false,
};

class Incidents extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  isPollRequestRunning: boolean = false;
  intervalId: null | ReturnType<typeof setInterval> = null;
  disposer: null | IReactionDisposer = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      setIncidents: action,
      reset: override,
      errorTypes: computed,
      incidentsCount: computed,
      toggleFlowNodeSelection: action,
      toggleErrorTypeSelection: action,
      clearSelection: action,
      setIncidentBarOpen: action,
      setFlowNodeSelection: action,
      setErrorTypeSelection: action,
    });
  }

  init() {
    this.disposer = autorun(() => {
      if (
        processInstanceDetailsStore.state.processInstance?.state === 'INCIDENT'
      ) {
        if (this.intervalId === null) {
          this.fetchIncidents(
            processInstanceDetailsStore.state.processInstance.id,
          );
          this.startPolling(
            processInstanceDetailsStore.state.processInstance.id,
          );
        }
      } else {
        this.stopPolling();
      }
    });
  }

  startPolling = async (
    instanceId: ProcessInstanceEntity['id'],
    options: {runImmediately?: boolean} = {runImmediately: false},
  ) => {
    if (
      document.visibilityState === 'hidden' ||
      !processInstanceDetailsStore.isRunning ||
      processInstanceDetailsStore.state.processInstance?.state !== 'INCIDENT'
    ) {
      return;
    }

    if (options.runImmediately) {
      if (!this.isPollRequestRunning) {
        this.handlePolling(instanceId);
      }
    }

    this.intervalId = setInterval(() => {
      if (!this.isPollRequestRunning) {
        this.handlePolling(instanceId);
      }
    }, 5000);
  };

  fetchIncidents = this.retryOnConnectionLost(async (id: string) => {
    const response = await fetchProcessInstanceIncidents(id);
    if (response.isSuccess) {
      this.setIncidents(response.data);
    }
  });

  stopPolling = () => {
    const {intervalId} = this;

    if (intervalId !== null) {
      clearInterval(intervalId);
      this.intervalId = null;
    }
  };

  handlePolling = async (id: string) => {
    this.isPollRequestRunning = true;
    const response = await fetchProcessInstanceIncidents(id, {isPolling: true});

    if (this.intervalId !== null && response.isSuccess) {
      this.setIncidents(response.data);
    }

    this.isPollRequestRunning = false;
  };

  setIncidents = (response: ProcessInstanceIncidentsDto) => {
    this.state.response = response;
    this.state.isLoaded = true;
  };

  reset() {
    super.reset();
    this.stopPolling();
    this.state = {...DEFAULT_STATE};
    this.disposer?.();
  }

  toggleFlowNodeSelection = (flowNodeId: string) => {
    const {selectedFlowNodes} = this.state;
    if (selectedFlowNodes.includes(flowNodeId)) {
      this.state.selectedFlowNodes.splice(
        selectedFlowNodes.indexOf(flowNodeId),
      );
    } else {
      this.state.selectedFlowNodes.push(flowNodeId);
    }
  };

  toggleErrorTypeSelection = (errorType: string) => {
    const {selectedErrorTypes} = this.state;
    if (selectedErrorTypes.includes(errorType)) {
      selectedErrorTypes.splice(selectedErrorTypes.indexOf(errorType));
    } else {
      selectedErrorTypes.push(errorType);
    }
  };

  setFlowNodeSelection = (selectedFlowNodes: string[]) => {
    this.state.selectedFlowNodes = selectedFlowNodes;
  };

  setErrorTypeSelection = (selectedErrorTypes: string[]) => {
    this.state.selectedErrorTypes = selectedErrorTypes;
  };

  clearSelection = () => {
    this.state.selectedErrorTypes = [];
    this.state.selectedFlowNodes = [];
  };

  setIncidentBarOpen = (isOpen: boolean) => {
    this.state.isIncidentBarOpen = isOpen;

    if (isOpen) {
      tracking.track({
        eventName: 'flow-node-incident-details-opened',
      });
    }
  };

  get errorTypes() {
    return this.state.response?.errorTypes || [];
  }

  get incidentsCount() {
    return this.state.response === null ? 0 : this.state.response.count;
  }
}

export const incidentsStore = new Incidents();
export type {Incident};
