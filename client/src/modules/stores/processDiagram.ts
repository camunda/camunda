/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeObservable, action, observable, override, computed} from 'mobx';
import {fetchProcessInstancesStatistics} from 'modules/api/instances';
import {getProcessInstancesRequestFilters} from 'modules/utils/filter';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {fetchProcessXML} from 'modules/api/diagram';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {getFlowNodes} from 'modules/utils/flowNodes';
import {processInstancesStore} from './processInstances';

type Node = {
  $type: string;
  id: string;
  name: string;
};

type NodeStatistics = {
  active: number;
  activityId: string;
  canceled: number;
  completed: number;
  incidents: number;
};

type State = {
  statistics: NodeStatistics[];
  diagramModel: {bpmnElements: unknown} | null;
  xml: string | null;
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'error';
};

type FlowNodeState = 'active' | 'incidents' | 'canceled' | 'completed';

const DEFAULT_STATE: State = {
  statistics: [],
  diagramModel: null,
  xml: null,
  status: 'initial',
};

const overlayPositions = {
  active: {
    bottom: 9,
    left: 0,
  },
  incidents: {
    bottom: 9,
    right: 0,
  },
  canceled: {
    top: -16,
    left: 0,
  },
  completed: {
    bottom: 1,
    left: 17,
  },
} as const;

class ProcessDiagram extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  processId: ProcessInstanceEntity['processId'] | null = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      reset: override,
      startFetching: action,
      handleFetchSuccess: action,
      handleFetchStatisticsSuccess: action,
      handleFetchError: action,
      selectableFlowNodes: computed,
      selectableIds: computed,
      flowNodeStates: computed,
      resetState: action,
      overlaysData: computed,
    });
  }

  init = () => {
    processInstancesStore.addCompletedOperationsHandler(() => {
      const filters = getProcessInstancesRequestFilters();
      const processIds = filters?.processIds ?? [];
      if (processIds.length > 0) {
        this.#fetchProcessStatistics();
      }
    });
  };

  fetchProcessDiagram: (
    processId: ProcessInstanceEntity['processId']
  ) => Promise<void> = this.retryOnConnectionLost(
    async (processId: ProcessInstanceEntity['processId']) => {
      try {
        this.startFetching();

        if (this.processId !== processId) {
          this.processId = processId;
          await this.#fetchProcessXmlAndStatistics(processId);
        } else {
          await this.#fetchProcessStatistics();
        }
      } catch (error) {
        this.handleFetchError(error);
      }
    }
  );

  #fetchProcessStatistics = async () => {
    const response = await fetchProcessInstancesStatistics(
      getProcessInstancesRequestFilters()
    );

    if (response.ok) {
      this.handleFetchStatisticsSuccess(await response.json());
    } else {
      this.handleFetchError();
    }
  };

  #fetchProcessXmlAndStatistics = async (
    processId: ProcessInstanceEntity['processId']
  ) => {
    const [processXMLResponse, processInstancesStatisticsResponse] =
      await Promise.all([
        fetchProcessXML(processId),
        fetchProcessInstancesStatistics(getProcessInstancesRequestFilters()),
      ]);

    if (processXMLResponse.ok && processInstancesStatisticsResponse.ok) {
      const xml = await processXMLResponse.text();
      const [parsedDiagram, statistics] = await Promise.all([
        parseDiagramXML(xml),
        processInstancesStatisticsResponse.json(),
      ]);

      this.handleFetchSuccess(xml, parsedDiagram, statistics);
    } else {
      this.handleFetchError();
    }
  };

  get selectableFlowNodes(): Node[] {
    return getFlowNodes(this.state.diagramModel?.bpmnElements);
  }

  get selectableIds() {
    return this.selectableFlowNodes.map(({id}) => id);
  }

  startFetching = () => {
    this.state.status = 'fetching';
  };

  handleFetchSuccess = (
    xml: string,
    parsedDiagramXml: any,
    statistics: any
  ) => {
    this.state.xml = xml;
    this.state.diagramModel = parsedDiagramXml;
    this.state.statistics = statistics;
    this.state.status = 'fetched';
  };

  handleFetchStatisticsSuccess = (statistics: any) => {
    this.state.statistics = statistics;
    this.state.status = 'fetched';
  };

  handleFetchError = (error?: unknown) => {
    this.state.xml = null;
    this.state.diagramModel = null;
    this.state.status = 'error';

    logger.error('Diagram failed to fetch');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  get flowNodeFilterOptions() {
    return this.selectableFlowNodes
      .map(({id, name}) => ({
        value: id,
        label: name ?? id,
      }))
      .sort((node, nextNode) => {
        const label = node.label.toUpperCase();
        const nextLabel = nextNode.label.toUpperCase();

        if (label < nextLabel) {
          return -1;
        }
        if (label > nextLabel) {
          return 1;
        }

        return 0;
      });
  }

  get overlaysData() {
    return this.flowNodeStates.map(({flowNodeState, count, flowNodeId}) => ({
      payload: {flowNodeState, count},
      type: `statistics-${flowNodeState}`,
      flowNodeId,
      position: overlayPositions[flowNodeState],
    }));
  }

  get flowNodeStates() {
    return this.state.statistics.flatMap((statistics) => {
      const types = ['active', 'incidents', 'canceled', 'completed'] as const;
      return types.reduce<
        {
          flowNodeId: string;
          count: number;
          flowNodeState: FlowNodeState;
        }[]
      >((states, flowNodeState) => {
        const count = statistics[flowNodeState];

        if (count > 0) {
          return [
            ...states,
            {
              flowNodeId: statistics.activityId,
              count,
              flowNodeState,
            },
          ];
        } else {
          return states;
        }
      }, []);
    });
  }

  resetState = () => {
    this.state = {...DEFAULT_STATE};
  };

  reset() {
    this.processId = null;
    super.reset();
    this.resetState();
  }
}

export type {FlowNodeState};
export const processDiagramStore = new ProcessDiagram();
