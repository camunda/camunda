/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {action, makeObservable, override} from 'mobx';
import {ReadonlyDeep} from 'ts-toolbelt/out/Object/Readonly';
import {fetchDrdData} from 'modules/api/decisions';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {logger} from 'modules/logger';

type DrdData = ReadonlyDeep<{
  [decisionId: string]: {
    decisionInstanceId: DecisionInstanceEntity['id'];
    state: DecisionInstanceEntityState;
  };
}>;

type State = {
  drdData: DrdData | null;
  status: 'initial' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  drdData: null,
  status: 'initial',
};

class Drd extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};

  constructor() {
    super();

    makeObservable(this, {
      handleFetchSuccess: action,
      handleFetchFailure: action,
      reset: override,
    });
  }

  fetchDrdData = this.retryOnConnectionLost(
    async (decisionInstanceId: DecisionInstanceEntity['id']) => {
      try {
        const response = await fetchDrdData(decisionInstanceId);

        if (response.ok) {
          this.handleFetchSuccess(await response.json());
        } else {
          this.handleFetchFailure();
        }
      } catch (error) {
        this.handleFetchFailure(error);
      }
    }
  );

  handleFetchSuccess = (drdData: DrdData) => {
    this.state.drdData = drdData;
    this.state.status = 'fetched';
  };

  handleFetchFailure = (error?: unknown) => {
    this.state.status = 'error';

    logger.error('Failed to fetch DRD data');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  reset() {
    super.reset();
    this.state = {...DEFAULT_STATE};
  }
}

export const drdDataStore = new Drd();
export type {DrdData};
