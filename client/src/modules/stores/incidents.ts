/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  makeObservable,
  computed,
  action,
  observable,
  autorun,
  IReactionDisposer,
  override,
} from 'mobx';
import {fetchProcessInstanceIncidents} from 'modules/api/instances';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {singleInstanceDiagramStore} from 'modules/stores/singleInstanceDiagram';
import {flowNodeSelectionStore} from './flowNodeSelection';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';

type FlowNode = {
  id: string;
  count: number;
};
type ErrorType = {
  id: string;
  name: string;
  count: number;
};
type Incident = {
  id: string;
  errorType: {
    id: string;
    name: string;
  };
  errorMessage: string;
  flowNodeId: string;
  flowNodeInstanceId: string;
  flowNodeName: string;
  jobId: string | null;
  creationTime: string;
  hasActiveOperation: boolean;
  lastOperation: null | unknown;
  rootCauseInstance: null | {
    instanceId: string;
    processDefinitionId: string;
    processDefinitionName: string;
  };
  isSelected: boolean;
};
type Response = {
  count: number;
  incidents: Incident[];
  errorTypes: ErrorType[];
  flowNodes: FlowNode[];
};

type State = {
  response: null | Response;
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
  intervalId: null | ReturnType<typeof setInterval> = null;
  disposer: null | IReactionDisposer = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      setIncidents: action,
      reset: override,
      incidents: computed,
      filteredIncidents: computed,
      flowNodes: computed,
      errorTypes: computed,
      incidentsCount: computed,
      toggleFlowNodeSelection: action,
      toggleErrorTypeSelection: action,
      clearSelection: action,
      setIncidentBarOpen: action,
    });
  }

  init() {
    this.disposer = autorun(() => {
      if (currentInstanceStore.state.instance?.state === 'INCIDENT') {
        if (this.intervalId === null) {
          this.fetchIncidents(currentInstanceStore.state.instance.id);
          this.startPolling(currentInstanceStore.state.instance.id);
        }
      } else {
        this.stopPolling();
      }
    });
  }

  startPolling = async (id: string) => {
    this.intervalId = setInterval(() => {
      this.handlePolling(id);
    }, 5000);
  };

  fetchIncidents = this.retryOnConnectionLost(async (id: string) => {
    const response = await fetchProcessInstanceIncidents(id);
    if (response.ok) {
      this.setIncidents(await response.json());
    }
  });

  stopPolling = () => {
    const {intervalId} = this;

    if (intervalId !== null) {
      clearInterval(intervalId);
      this.intervalId = null;
    }
  };

  handlePolling = async (id: any) => {
    const response = await fetchProcessInstanceIncidents(id);
    if (this.intervalId !== null && response.ok) {
      this.setIncidents(await response.json());
    }
  };

  setIncidents = (response: any) => {
    this.state.response = response;
    this.state.isLoaded = true;
  };

  reset() {
    super.reset();
    this.stopPolling();
    this.state = {...DEFAULT_STATE};
    this.disposer?.();
  }

  getIncidentType = (flowNodeInstanceId: string) => {
    const incident = this.incidents.find(
      (incident) => incident.flowNodeInstanceId === flowNodeInstanceId
    );

    return incident?.errorType;
  };

  toggleFlowNodeSelection = (flowNodeId: string) => {
    const {selectedFlowNodes} = this.state;
    if (selectedFlowNodes.includes(flowNodeId)) {
      this.state.selectedFlowNodes.splice(
        selectedFlowNodes.indexOf(flowNodeId)
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

  clearSelection = () => {
    this.state.selectedErrorTypes = [];
    this.state.selectedFlowNodes = [];
  };

  setIncidentBarOpen = (isOpen: boolean) => {
    this.state.isIncidentBarOpen = isOpen;
  };

  get filteredIncidents() {
    const {selectedFlowNodes, selectedErrorTypes} = incidentsStore.state;

    const hasSelectedFlowNodes = selectedFlowNodes.length > 0;
    const hasSelectedErrorTypes = selectedErrorTypes.length > 0;

    if (!hasSelectedFlowNodes && !hasSelectedErrorTypes) {
      return this.incidents;
    }

    return this.incidents.filter((incident) => {
      if (hasSelectedErrorTypes && hasSelectedFlowNodes) {
        return (
          selectedErrorTypes.includes(incident.errorType.id) &&
          selectedFlowNodes.includes(incident.flowNodeId)
        );
      }
      if (hasSelectedErrorTypes) {
        return selectedErrorTypes.includes(incident.errorType.id);
      }
      if (hasSelectedFlowNodes) {
        return selectedFlowNodes.includes(incident.flowNodeId);
      }
      return [];
    });
  }

  get incidents() {
    if (this.state.response === null) {
      return [];
    }
    return this.state.response.incidents.map((incident) => ({
      ...incident,
      flowNodeName: singleInstanceDiagramStore.getFlowNodeName(
        incident.flowNodeId
      ),
      isSelected: flowNodeSelectionStore.isSelected({
        flowNodeId: incident.flowNodeId,
        flowNodeInstanceId: incident.flowNodeInstanceId,
        isMultiInstance: false,
      }),
    }));
  }

  isSingleIncidentSelected(flowNodeInstanceId: string) {
    const selectedInstances = this.incidents.filter(
      (incident) => incident.isSelected
    );

    return (
      selectedInstances.length === 1 &&
      selectedInstances[0]?.flowNodeInstanceId === flowNodeInstanceId
    );
  }

  get flowNodes() {
    if (this.state.response === null) {
      return [];
    }

    return this.state.response.flowNodes.map((flowNode) => ({
      ...flowNode,
      name: singleInstanceDiagramStore.getFlowNodeName(flowNode.id),
    }));
  }

  get errorTypes() {
    return this.state.response?.errorTypes || [];
  }

  get incidentsCount() {
    return this.state.response === null ? 0 : this.state.response.count;
  }
}

export const incidentsStore = new Incidents();
export type {Incident};
