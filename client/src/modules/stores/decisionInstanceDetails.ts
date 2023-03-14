/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeObservable, override, action, observable} from 'mobx';
import {logger} from 'modules/logger';
import {
  fetchDecisionInstance,
  DecisionInstanceDto,
} from 'modules/api/decisionInstances/fetchDecisionInstance';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';

type State = {
  decisionInstance: DecisionInstanceDto | null;
  decisionInstanceId: string | null;
  status: 'initial' | 'fetched' | 'error' | 'forbidden';
};

const DEFAULT_STATE: State = {
  decisionInstance: null,
  decisionInstanceId: null,
  status: 'initial',
};

class DecisionInstanceDetails extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      handleFetchSuccess: action,
      handleFetchFailure: action,
      reset: override,
    });
  }

  fetchDecisionInstance = this.retryOnConnectionLost(
    async (decisionInstanceId: string) => {
      const response = await fetchDecisionInstance(decisionInstanceId);

      if (response.isSuccess) {
        this.handleFetchSuccess(response.data, decisionInstanceId);
      } else {
        this.handleFetchFailure(response.statusCode);
      }
    }
  );

  handleFetchSuccess = (
    decisionInstance: DecisionInstanceDto,
    decisionInstanceId: string
  ) => {
    this.state.decisionInstance = decisionInstance;
    this.state.decisionInstanceId = decisionInstanceId;
    this.state.status = 'fetched';
  };

  handleFetchFailure = (statusCode: number) => {
    logger.error('Failed to fetch decision instance');
    if (statusCode === 403) {
      this.state.status = 'forbidden';
      return;
    }

    this.state.status = 'error';
  };

  reset() {
    super.reset();
    this.state = {...DEFAULT_STATE};
  }
}

export const decisionInstanceDetailsStore = new DecisionInstanceDetails();
