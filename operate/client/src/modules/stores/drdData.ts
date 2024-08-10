/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  action,
  computed,
  IReactionDisposer,
  makeObservable,
  observable,
  override,
  reaction,
} from 'mobx';
import {
  fetchDrdData,
  DrdDataDto,
} from 'modules/api/decisionInstances/fetchDrdData';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';
import {decisionInstanceDetailsStore} from './decisionInstanceDetails';

type DecisionStateOverlay = {
  state: DecisionInstanceEntityState;
  container: HTMLDivElement;
  decisionId: string;
};

type State = {
  drdData: DrdDataDto | null;
  status: 'initial' | 'fetched' | 'error';
  decisionStateOverlays: DecisionStateOverlay[];
};

const DEFAULT_STATE: State = {
  drdData: null,
  status: 'initial',
  decisionStateOverlays: [],
};

class Drd extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  disposer: IReactionDisposer | null = null;

  constructor() {
    super();

    makeObservable(this, {
      handleFetchSuccess: action,
      handleFetchFailure: action,
      addDecisionStateOverlay: action,
      clearDecisionStateOverlays: action,
      currentDecision: computed,
      selectableDecisions: computed,
      decisionStates: computed,
      state: observable,
      reset: override,
    });
  }

  init = () => {
    this.disposer = reaction(
      () =>
        decisionInstanceDetailsStore.state.decisionInstance
          ?.decisionDefinitionId,
      (decisionDefinitionId) => {
        if (decisionDefinitionId !== undefined) {
          this.fetchDrdData(
            decisionInstanceDetailsStore.state.decisionInstanceId,
          );
        }
      },
    );
  };

  fetchDrdData = this.retryOnConnectionLost(
    async (decisionInstanceId: DecisionInstanceEntity['id']) => {
      const response = await fetchDrdData(decisionInstanceId);

      if (response.isSuccess) {
        this.handleFetchSuccess(response.data);
      } else {
        this.handleFetchFailure();
      }
    },
  );

  handleFetchSuccess = (drdData: DrdDataDto) => {
    this.state.drdData = drdData;
    this.state.status = 'fetched';
  };

  handleFetchFailure = (error?: unknown) => {
    this.state.status = 'error';
  };

  addDecisionStateOverlay = (decisionStateOverlay: DecisionStateOverlay) => {
    this.state.decisionStateOverlays.push(decisionStateOverlay);
  };

  clearDecisionStateOverlays = () => {
    this.state.decisionStateOverlays = [];
  };

  get currentDecision() {
    const {drdData} = this.state;

    if (drdData === null) {
      return null;
    }

    return (
      Object.keys(drdData).find((decisionId) =>
        drdData[decisionId]?.some((drdData) => {
          return (
            drdData.decisionInstanceId ===
            decisionInstanceDetailsStore.state.decisionInstanceId
          );
        }),
      ) ?? null
    );
  }

  get selectableDecisions() {
    if (this.state.drdData === null) {
      return [];
    }

    return Object.keys(this.state.drdData);
  }

  get decisionStates() {
    if (this.state.drdData === null) {
      return [];
    }

    return Object.entries(this.state.drdData).map(
      ([decisionId, decisionInstances]) => {
        return {
          decisionId,
          state: decisionInstances[decisionInstances.length - 1]!.state,
        };
      },
    );
  }

  reset() {
    super.reset();
    this.disposer?.();
    this.state = {...DEFAULT_STATE};
  }
}

export const drdDataStore = new Drd();
