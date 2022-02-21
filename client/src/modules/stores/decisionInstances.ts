/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {makeObservable, observable, action, override, computed} from 'mobx';

import {fetchDecisionInstances} from 'modules/api/decisions';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {getSortParams} from 'modules/utils/filter';

type State = {
  decisionInstances: DecisionInstanceEntity[];
  filteredInstancesCount: number;
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  decisionInstances: [],
  filteredInstancesCount: 0,
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
      handleFetchSuccess: action,
      handleFetchError: action,
      setDecisionInstances: action,
      areDecisionInstancesEmpty: computed,
    });
  }

  fetchInstancesFromFilters = this.retryOnConnectionLost(async () => {
    this.startFetching();
    this.fetchInstances({
      query: {},
      sorting: getSortParams() || {sortBy: 'evaluationTime', sortOrder: 'desc'},
    });
  });

  fetchInstances = async (
    payload: Parameters<typeof fetchDecisionInstances>['0']
  ) => {
    try {
      const response = await fetchDecisionInstances(payload);

      if (response.ok) {
        const {decisionInstances, totalCount} = await response.json();

        this.setDecisionInstances({decisionInstances, totalCount});
        this.handleFetchSuccess();
      } else {
        this.handleFetchError();
      }
    } catch (error) {
      this.handleFetchError(error);
    }
  };

  startFetching = () => {
    if (this.state.status === 'initial') {
      this.state.status = 'first-fetch';
    } else {
      this.state.status = 'fetching';
    }
    this.state.filteredInstancesCount = 0;
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

  reset() {
    super.reset();
    this.state = {
      ...DEFAULT_STATE,
    };
  }
}

export const decisionInstancesStore = new DecisionInstances();
