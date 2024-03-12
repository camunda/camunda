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

import {makeObservable, action, observable, override, computed} from 'mobx';
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
  INCIDENTS_BADGE,
} from 'modules/bpmn-js/badgePositions';
import {
  RequestFilters,
  getProcessInstancesRequestFilters,
} from 'modules/utils/filter';

type State = {
  statistics: ProcessInstancesStatisticsDto[];
  status: 'initial' | 'fetching' | 'fetched' | 'error';
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
      overlaysData: computed,
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

  get overlaysData() {
    return this.flowNodeStates.map(({flowNodeState, count, flowNodeId}) => ({
      payload: {flowNodeState, count},
      type: `statistics-${flowNodeState}`,
      flowNodeId,
      position: overlayPositions[flowNodeState],
    }));
  }

  get flowNodeStates() {
    return this.statistics.flatMap((statistics) => {
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
    super.reset();
    this.resetState();
  }
}

export {ProcessStatistics};
