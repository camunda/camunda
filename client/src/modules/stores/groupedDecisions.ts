/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  action,
  computed,
  IReactionDisposer,
  makeObservable,
  observable,
  override,
} from 'mobx';
import {logger} from 'modules/logger';
import {ReadonlyDeep} from 'ts-toolbelt/out/Object/Readonly';
import {fetchGroupedDecisions} from 'modules/api/decisions';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';

type Decisions = ReadonlyDeep<
  [
    {
      decisionId: string;
      name: string;
      decisions: [
        {
          id: string;
          name: string;
          version: number;
          decisionId: string;
        }
      ];
    }
  ]
>;

type State = {
  decisions: Decisions | [];
  status: 'initial' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  decisions: [],
  status: 'initial',
};

class GroupedDecisions extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  disposer: IReactionDisposer | null = null;

  constructor() {
    super();

    makeObservable(this, {
      state: observable,
      handleFetchSuccess: action,
      handleFetchFailure: action,
      reset: override,
      areDecisionsEmpty: computed,
    });
  }

  fetchDecisions = this.retryOnConnectionLost(async () => {
    try {
      const response = await fetchGroupedDecisions();

      if (response.ok) {
        this.handleFetchSuccess(await response.json());
      } else {
        this.handleFetchFailure();
      }
    } catch (error) {
      this.handleFetchFailure(error);
    }
  });

  handleFetchSuccess = (decisions: Decisions) => {
    this.state.decisions = decisions;
    this.state.status = 'fetched';
  };

  handleFetchFailure = (error?: unknown) => {
    this.state.status = 'error';

    logger.error('Failed to fetch grouped decisions');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  getDecisionDefinitionId = ({
    decisionId,
    version,
  }: {
    decisionId: string;
    version: number;
  }) => {
    const {decisions} = this.state;

    return (
      decisions
        .find((decision) => decision.decisionId === decisionId)
        ?.decisions.find((decision) => decision.version === version)?.id ?? null
    );
  };

  get areDecisionsEmpty() {
    return this.state.decisions.length === 0;
  }

  reset() {
    super.reset();
    this.state = {...DEFAULT_STATE};
  }
}

export const groupedDecisionsStore = new GroupedDecisions();
