/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeObservable, action, observable, override, computed} from 'mobx';
import {BusinessObject, OverlayPosition} from 'bpmn-js/lib/NavigatedViewer';
import {logger} from 'modules/logger';
import {
  ProcessInstancesStatisticsDto,
  ProcessInstancesStatisticsRequest,
  fetchProcessInstancesStatistics,
} from 'modules/api/processInstances/fetchProcessInstancesStatistics';
import {NetworkReconnectionHandler} from '../networkReconnectionHandler';
import {
  ACTIVE_BADGE,
  CANCELED_BADGE,
  COMPLETED_BADGE,
  COMPLETED_END_EVENT_BADGE,
  INCIDENTS_BADGE,
  SUBPROCESS_WITH_INCIDENTS,
} from 'modules/bpmn-js/badgePositions';
import {
  RequestFilters,
  getProcessInstancesRequestFilters,
} from 'modules/utils/filter';

type State = {
  statistics: ProcessInstancesStatisticsDto[];
  status: 'initial' | 'fetching' | 'fetched' | 'error';
};

type SubprocessOverlay = {
  payload: {
    flowNodeState: string;
  };
  type: string;
  flowNodeId: string;
  position: OverlayPosition;
};

const DEFAULT_STATE: State = {
  statistics: [],
  status: 'initial',
};

const overlayPositions = {
  active: ACTIVE_BADGE,
  incidents: INCIDENTS_BADGE,
  canceled: CANCELED_BADGE,
  completed: COMPLETED_BADGE,
  completedEndEvents: COMPLETED_END_EVENT_BADGE,
  subprocessWithIncidents: SUBPROCESS_WITH_INCIDENTS,
} as const;

class ProcessStatistics extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      reset: override,
      statistics: computed,
      setStatistics: action,
      setStatus: action,
      startFetching: action,
      flowNodeStates: computed,
      resetState: action,
      getOverlaysData: action,
    });
  }

  startFetching = () => {
    this.setStatus('fetching');
    this.state.statistics = [];
  };

  fetchProcessStatistics: (
    requestFilters?: ProcessInstancesStatisticsRequest,
  ) => Promise<void> = this.retryOnConnectionLost(
    async (requestFilters?: RequestFilters) => {
      this.startFetching();
      const response = await fetchProcessInstancesStatistics({
        ...getProcessInstancesRequestFilters(),
        ...requestFilters,
      });

      if (response.isSuccess) {
        this.setStatistics(response.data);
      } else {
        this.handleFetchError();
      }
    },
  );

  setStatus = (status: State['status']) => {
    this.state.status = status;
  };

  handleFetchError = (error?: unknown) => {
    logger.error('Failed to fetch diagram statistics');
    if (error !== undefined) {
      this.setStatus('error');
      logger.error(error);
    }
  };

  get statistics() {
    return this.state.statistics;
  }

  setStatistics = (statistics: ProcessInstancesStatisticsDto[]) => {
    this.setStatus('fetched');
    this.state.statistics = statistics;
  };

  getOverlaysData(selectableFlowNodes: BusinessObject[]) {
    const overlaysMinusSubprocesses = this.flowNodeStates.map(
      ({flowNodeState: originalFlowNodeState, count, flowNodeId}) => {
        const flowNodeState =
          originalFlowNodeState === 'completed'
            ? 'completedEndEvents'
            : originalFlowNodeState;

        return {
          payload: {flowNodeState, count},
          type: `statistics-${flowNodeState}`,
          flowNodeId,
          position: overlayPositions[flowNodeState],
        };
      },
    );

    if (selectableFlowNodes.length === 0) return overlaysMinusSubprocesses;

    const flowNodeIdsWithIncidents = this.flowNodeStates
      .filter(({flowNodeState}) => flowNodeState === 'incidents')
      .map((flowNode) => flowNode.flowNodeId);

    const selectableFlowNodesWithIncidents = selectableFlowNodes.filter(
      ({id}) => flowNodeIdsWithIncidents.includes(id),
    );

    const subprocessOverlays: SubprocessOverlay[] = [];

    selectableFlowNodesWithIncidents.forEach((flowNode) => {
      while (flowNode.$parent) {
        const parent = flowNode.$parent;
        if (parent.$type === 'bpmn:SubProcess') {
          subprocessOverlays.push({
            payload: {flowNodeState: 'incidents'},
            type: 'statistics-incidents',
            flowNodeId: parent.id,
            position: overlayPositions.subprocessWithIncidents,
          });
        }
        flowNode = parent;
      }
    });

    const allOverlays = [...overlaysMinusSubprocesses, ...subprocessOverlays];

    return allOverlays;
  }

  get flowNodeStates() {
    return this.statistics.flatMap((statistics) => {
      const types = ['active', 'incidents', 'canceled', 'completed'] as const;
      const flowNodeStatesPerType = types.reduce<
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

      return flowNodeStatesPerType;
    });
  }

  resetState = () => {
    this.state = {...DEFAULT_STATE};
  };

  reset() {
    super.reset();
    this.resetState();
  }
}

export {ProcessStatistics};
