/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeObservable, override, action, observable} from 'mobx';
import {logger} from 'modules/logger';
import {fetchDecisionInstance} from 'modules/api/decisions';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {ReadonlyDeep} from 'ts-toolbelt/out/Object/Readonly';

type DecisionInstanceType = ReadonlyDeep<{
  id: string;
  decisionId: string;
  decisionDefinitionId: string;
  state: DecisionInstanceEntityState;
  decisionName: string;
  decisionVersion: number;
  evaluationDate: string;
  processInstanceId: string | null;
  errorMessage: string | null;
  evaluatedInputs: Array<{
    id: string;
    name: string;
    value: string | null;
  }>;
  evaluatedOutputs: Array<{
    id: string;
    ruleIndex: number;
    ruleId: string;
    name: string;
    value: string | null;
  }>;
  result: string | null;
  decisionType: 'DECISION_TABLE' | 'LITERAL_EXPRESSION';
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

export const decisionInstanceDetailsStore = new DecisionInstanceDetails();
export type {DecisionInstanceType};
