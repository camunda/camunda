/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {makeObservable, observable, action, override, computed} from 'mobx';

import {fetchDecisionInstances} from 'modules/api/decisions';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';

type State = {
  decisionInstances: DecisionInstanceEntity[];

  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  decisionInstances: [],
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

  fetchInstances = this.retryOnConnectionLost(async () => {
    try {
      const response = await fetchDecisionInstances({query: {}});

      if (response.ok) {
        const {decisionInstances} = await response.json();

        this.setDecisionInstances(decisionInstances);
        this.handleFetchSuccess();
      } else {
        this.handleFetchError();
      }
    } catch (error) {
      this.handleFetchError(error);
    }
  });

  startFetching = () => {
    if (this.state.status === 'initial') {
      this.state.status = 'first-fetch';
    } else {
      this.state.status = 'fetching';
    }
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

  setDecisionInstances = (decisionInstances: DecisionInstanceEntity[]) => {
    this.state.decisionInstances = decisionInstances;
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
