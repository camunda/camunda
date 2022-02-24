/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {makeObservable, override, action, observable} from 'mobx';
import {logger} from 'modules/logger';
import {fetchDecisionInstance} from 'modules/api/decisions';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {ReadonlyDeep} from 'ts-toolbelt/out/Object/Readonly';

type DecisionInstanceType = ReadonlyDeep<{
  decisionId: string;
  decisionDefinitionId: string;
  state: 'FAILED' | 'COMPLETED';
  name: string;
  version: string;
  evaluationDate: string;
  processInstanceId: string | null;
  inputs: Array<{
    id: string;
    name: string;
    value: string;
  }>;
  outputs: Array<{
    id: string;
    ruleIndex: number;
    rule: number;
    name: string;
    value: string;
  }>;
  result: string;
}>;

type State = {
  decisionInstance: DecisionInstanceType | null;
  decisionInstanceId: string | null;
  status: 'initial' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  decisionInstance: null,
  decisionInstanceId: null,
  status: 'initial',
};

class DecisionInstance extends NetworkReconnectionHandler {
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
      try {
        const response = await fetchDecisionInstance(decisionInstanceId);

        if (response.ok) {
          this.handleFetchSuccess(await response.json(), decisionInstanceId);
        } else {
          this.handleFetchFailure();
        }
      } catch (error) {
        this.handleFetchFailure(error);
      }
    }
  );

  handleFetchSuccess = (
    decisionInstance: DecisionInstanceType,
    decisionInstanceId: string
  ) => {
    this.state.decisionInstance = decisionInstance;
    this.state.decisionInstanceId = decisionInstanceId;
    this.state.status = 'fetched';
  };

  handleFetchFailure = (error?: unknown) => {
    this.state.status = 'error';

    logger.error('Failed to fetch decision instance');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  reset() {
    super.reset();
    this.state = {...DEFAULT_STATE};
  }
}

export const decisionInstanceStore = new DecisionInstance();
export type {DecisionInstanceType};
