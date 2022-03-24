/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {makeObservable, observable, action, override, computed} from 'mobx';

import {fetchDecisionInstances} from 'modules/api/decisions';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {
  getDecisionInstancesRequestFilters,
  getSortParams,
} from 'modules/utils/filter';

type FetchType = 'initial' | 'prev' | 'next';
type State = {
  decisionInstances: DecisionInstanceEntity[];
  filteredInstancesCount: number;
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

const MAX_INSTANCES_STORED = 200;
const MAX_INSTANCES_PER_REQUEST = 50;

const DEFAULT_STATE: State = {
  decisionInstances: [],
  filteredInstancesCount: 0,
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

  fetchInstancesFromFilters = this.retryOnConnectionLost(async () => {
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
    try {
      const response = await fetchDecisionInstances(payload);
      if (response.ok) {
        const {decisionInstances, totalCount} = await response.json();

        this.setDecisionInstances({
          decisionInstances: this.getDecisionInstances(
            fetchType,
            decisionInstances
          ),
          totalCount,
        });

        this.setLatestFetchDetails(fetchType, decisionInstances.length);

        this.handleFetchSuccess();
      } else {
        this.handleFetchError();
      }
    } catch (error) {
      this.handleFetchError(error);
    }
  };

  shouldFetchPreviousInstances = () => {
    const {latestFetch, decisionInstances, status} = this.state;
    if (['fetching-prev', 'fetching-next', 'fetching'].includes(status)) {
      return false;
    }

    return (
      (latestFetch?.fetchType === 'next' &&
        decisionInstances.length === MAX_INSTANCES_STORED) ||
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
        decisionInstances.length === MAX_INSTANCES_STORED) ||
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
    this.state.filteredInstancesCount = 0;
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

  handleFetchError = (error?: unknown) => {
    this.state.status = 'error';
    this.state.decisionInstances = [];

    logger.error('Failed to fetch decision instances');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  setLatestFetchDetails = (
    fetchType: FetchType,
    decisionInstancesCount: number
  ) => {
    this.state.latestFetch = {
      fetchType,
      decisionInstancesCount,
    };
  };

  getDecisionInstances = (
    fetchType: FetchType,
    decisionInstances: DecisionInstanceEntity[]
  ) => {
    switch (fetchType) {
      case 'next':
        const allDecisionInstances = [
          ...this.state.decisionInstances,
          ...decisionInstances,
        ];

        return allDecisionInstances.slice(
          Math.max(allDecisionInstances.length - MAX_INSTANCES_STORED, 0)
        );
      case 'prev':
        return [...decisionInstances, ...this.state.decisionInstances].slice(
          0,
          MAX_INSTANCES_STORED
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
    this.state.filteredInstancesCount = totalCount;
  };

  get areDecisionInstancesEmpty() {
    return (
      this.state.status === 'fetched' &&
      this.state.decisionInstances.length === 0
    );
  }

  get hasLatestDecisionInstances() {
    return (
      this.state.decisionInstances.length === MAX_INSTANCES_STORED &&
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
