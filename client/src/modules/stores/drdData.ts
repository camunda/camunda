/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  action,
  IReactionDisposer,
  makeObservable,
  override,
  reaction,
} from 'mobx';
import {ReadonlyDeep} from 'ts-toolbelt/out/Object/Readonly';
import {fetchDrdData} from 'modules/api/decisions';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {logger} from 'modules/logger';
import {decisionInstanceStore} from './decisionInstance';

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
  disposer: IReactionDisposer | null = null;

  constructor() {
    super();

    makeObservable(this, {
      handleFetchSuccess: action,
      handleFetchFailure: action,
      reset: override,
    });
  }

  init = () => {
    this.disposer = reaction(
      () => decisionInstanceStore.state.decisionInstance?.decisionDefinitionId,
      (decisionDefinitionId) => {
        if (decisionDefinitionId !== undefined) {
          this.fetchDrdData(decisionInstanceStore.state.decisionInstanceId);
        }
      }
    );
  };
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

  get currentDecision() {
    const {drdData} = this.state;

    if (drdData === null) {
      return null;
    }

    return (
      Object.keys(drdData).find(
        (decisionId) =>
          drdData[decisionId].decisionInstanceId ===
          decisionInstanceStore.state.decisionInstanceId
      ) ?? null
    );
  }

  reset() {
    super.reset();
    this.disposer?.();
    this.state = {...DEFAULT_STATE};
  }
}

export const drdDataStore = new Drd();
export type {DrdData};
