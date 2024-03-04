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

import {makeObservable, observable, action, override, computed} from 'mobx';

import {fetchDecisionInstances} from 'modules/api/decisionInstances/fetchDecisionInstances';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {
  getDecisionInstancesRequestFilters,
  getSortParams,
} from 'modules/utils/filter';
import {tracking} from 'modules/tracking';

type FetchType = 'initial' | 'prev' | 'next';
type State = {
  decisionInstances: DecisionInstanceEntity[];
  filteredDecisionInstancesCount: number;
  latestFetch: {
    fetchType: FetchType;
    decisionInstancesCount: number;
  } | null;
  status:
    | 'initial'
    | 'first-fetch'
    | 'fetching'
    | 'fetching-next'
    | 'fetching-prev'
    | 'fetched'
    | 'error';
};

const MAX_PROCESS_INSTANCES_STORED = 200;
const MAX_INSTANCES_PER_REQUEST = 50;

const DEFAULT_STATE: State = {
  decisionInstances: [],
  filteredDecisionInstancesCount: 0,
  latestFetch: null,
  status: 'initial',
};

class DecisionInstances extends NetworkReconnectionHandler {
  state: State = {
    ...DEFAULT_STATE,
  };

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      reset: override,
      startFetching: action,
      startFetchingNext: action,
      startFetchingPrev: action,
      handleFetchSuccess: action,
      handleFetchError: action,
      setDecisionInstances: action,
      areDecisionInstancesEmpty: computed,
      setLatestFetchDetails: action,
      hasLatestDecisionInstances: computed,
    });
  }

  fetchDecisionInstancesFromFilters = this.retryOnConnectionLost(async () => {
    this.startFetching();
    this.fetchInstances({
      fetchType: 'initial',
      payload: {
        query: getDecisionInstancesRequestFilters(),
        sorting: getSortParams() || {
          sortBy: 'evaluationDate',
          sortOrder: 'desc',
        },
        pageSize: MAX_INSTANCES_PER_REQUEST,
      },
    });
  });

  fetchInstances = async ({
    fetchType,
    payload,
  }: {
    fetchType: FetchType;
    payload: Parameters<typeof fetchDecisionInstances>['0'];
  }) => {
    const response = await fetchDecisionInstances(payload);
    if (response.isSuccess) {
      const {decisionInstances, totalCount} = response.data;

      tracking.track({
        eventName: 'decisions-loaded',
        filters: Object.keys(payload.query),
        ...payload.sorting,
      });

      this.setDecisionInstances({
        decisionInstances: this.getDecisionInstances(
          fetchType,
          decisionInstances,
        ),
        totalCount,
      });

      this.setLatestFetchDetails(fetchType, decisionInstances.length);

      this.handleFetchSuccess();
    } else {
      this.handleFetchError();
    }
  };

  shouldFetchPreviousInstances = () => {
    const {latestFetch, decisionInstances, status} = this.state;
    if (['fetching-prev', 'fetching-next', 'fetching'].includes(status)) {
      return false;
    }

    return (
      (latestFetch?.fetchType === 'next' &&
        decisionInstances.length === MAX_PROCESS_INSTANCES_STORED) ||
      (latestFetch?.fetchType === 'prev' &&
        latestFetch?.decisionInstancesCount === MAX_INSTANCES_PER_REQUEST)
    );
  };

  shouldFetchNextInstances = () => {
    const {latestFetch, decisionInstances, status} = this.state;
    if (['fetching-prev', 'fetching-next', 'fetching'].includes(status)) {
      return false;
    }

    return (
      (latestFetch?.fetchType === 'next' &&
        latestFetch?.decisionInstancesCount === MAX_INSTANCES_PER_REQUEST) ||
      (latestFetch?.fetchType === 'prev' &&
        decisionInstances.length === MAX_PROCESS_INSTANCES_STORED) ||
      latestFetch?.fetchType === 'initial'
    );
  };

  fetchPreviousInstances = async () => {
    this.startFetchingPrev();

    return this.fetchInstances({
      fetchType: 'prev',
      payload: {
        query: getDecisionInstancesRequestFilters(),
        sorting: getSortParams() || {
          sortBy: 'evaluationDate',
          sortOrder: 'desc',
        },
        searchBefore: this.state.decisionInstances[0]?.sortValues,
        pageSize: MAX_INSTANCES_PER_REQUEST,
      },
    });
  };

  fetchNextInstances = async () => {
    this.startFetchingNext();

    return this.fetchInstances({
      fetchType: 'next',
      payload: {
        query: getDecisionInstancesRequestFilters(),
        sorting: getSortParams() || {
          sortBy: 'evaluationDate',
          sortOrder: 'desc',
        },
        searchAfter:
          this.state.decisionInstances[this.state.decisionInstances.length - 1]
            ?.sortValues,
        pageSize: MAX_INSTANCES_PER_REQUEST,
      },
    });
  };

  startFetching = () => {
    if (this.state.status === 'initial') {
      this.state.status = 'first-fetch';
    } else {
      this.state.status = 'fetching';
    }
    this.state.filteredDecisionInstancesCount = 0;
  };

  startFetchingNext = () => {
    this.state.status = 'fetching-next';
  };

  startFetchingPrev = () => {
    this.state.status = 'fetching-prev';
  };

  handleFetchSuccess = () => {
    this.state.status = 'fetched';
  };

  handleFetchError = () => {
    this.state.status = 'error';
    this.state.decisionInstances = [];
  };

  setLatestFetchDetails = (
    fetchType: FetchType,
    decisionInstancesCount: number,
  ) => {
    this.state.latestFetch = {
      fetchType,
      decisionInstancesCount,
    };
  };

  getDecisionInstances = (
    fetchType: FetchType,
    decisionInstances: DecisionInstanceEntity[],
  ) => {
    switch (fetchType) {
      case 'next':
        const allDecisionInstances = [
          ...this.state.decisionInstances,
          ...decisionInstances,
        ];

        return allDecisionInstances.slice(
          Math.max(
            allDecisionInstances.length - MAX_PROCESS_INSTANCES_STORED,
            0,
          ),
        );
      case 'prev':
        return [...decisionInstances, ...this.state.decisionInstances].slice(
          0,
          MAX_PROCESS_INSTANCES_STORED,
        );
      case 'initial':
      default:
        return decisionInstances;
    }
  };

  setDecisionInstances = ({
    decisionInstances,
    totalCount,
  }: {
    decisionInstances: DecisionInstanceEntity[];
    totalCount: number;
  }) => {
    this.state.decisionInstances = decisionInstances;
    this.state.filteredDecisionInstancesCount = totalCount;
  };

  get areDecisionInstancesEmpty() {
    return (
      this.state.status === 'fetched' &&
      this.state.decisionInstances.length === 0
    );
  }

  get hasLatestDecisionInstances() {
    return (
      this.state.decisionInstances.length === MAX_PROCESS_INSTANCES_STORED &&
      this.state.latestFetch !== null &&
      this.state.latestFetch?.decisionInstancesCount !== 0
    );
  }

  reset() {
    super.reset();
    this.state = {
      ...DEFAULT_STATE,
    };
  }
}

export const decisionInstancesStore = new DecisionInstances();
