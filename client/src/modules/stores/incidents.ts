/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
import {
  fetchProcessInstanceIncidents,
  ProcessInstanceIncidentsDto,
  IncidentDto,
} from 'modules/api/processInstances/fetchProcessInstanceIncidents';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {processInstanceDetailsDiagramStore} from 'modules/stores/processInstanceDetailsDiagram';
import {tracking} from 'modules/tracking';
import {flowNodeSelectionStore} from './flowNodeSelection';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';

type Incident = IncidentDto & {isSelected: boolean; flowNodeName: string};
type ProcessInstanceIncidents = Omit<
  ProcessInstanceIncidentsDto,
  'incidents'
> & {
  incidents: Incident[];
};

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
      incidents: computed,
      filteredIncidents: computed,
      flowNodes: computed,
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
            processInstanceDetailsStore.state.processInstance.id
          );
          this.startPolling(
            processInstanceDetailsStore.state.processInstance.id
          );
        }
      } else {
        this.stopPolling();
      }
    });
  }

  startPolling = async (id: string) => {
    this.intervalId = setInterval(() => {
      if (!this.isPollRequestRunning) {
        this.handlePolling(id);
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
    const response = await fetchProcessInstanceIncidents(id);

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
      flowNodeName: processInstanceDetailsDiagramStore.getFlowNodeName(
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
      name: processInstanceDetailsDiagramStore.getFlowNodeName(flowNode.id),
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
export type {ProcessInstanceIncidents, Incident};
