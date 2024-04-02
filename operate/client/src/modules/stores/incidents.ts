/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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

  getIncidentType = (flowNodeInstanceId: string) => {
    const incident = this.incidents.find(
      (incident) => incident.flowNodeInstanceId === flowNodeInstanceId,
    );

    return incident?.errorType;
  };

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
        incident.flowNodeId,
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
      (incident) => incident.isSelected,
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
