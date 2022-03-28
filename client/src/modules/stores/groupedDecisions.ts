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
import {sortOptions} from 'modules/utils/sortOptions';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';

type Decision = ReadonlyDeep<{
  decisionId: string;
  name: string | null;
  decisions: {
    id: string;
    version: number;
    decisionId: string;
  }[];
}>;

type State = {
  decisions: ReadonlyArray<Decision>;
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
      decisions: computed,
      areDecisionsEmpty: computed,
      decisionVersionsById: computed,
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

  handleFetchSuccess = (decisions: Decision[]) => {
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

  get decisions() {
    return this.state.decisions
      .map(({decisionId, name}) => ({
        value: decisionId,
        label: name ?? decisionId,
      }))
      .sort(sortOptions);
  }

  get decisionVersionsById() {
    return this.state.decisions.reduce<{
      [decisionId: string]: Decision['decisions'];
    }>((decisions, decision) => {
      return {
        ...decisions,
        [decision.decisionId]: [...decision.decisions].sort(
          (decisionA, decisionB) => decisionA.version - decisionB.version
        ),
      };
    }, {});
  }

  getDecisionName = (decisionId: string | null) => {
    const decision = this.state.decisions.find(
      (decision) => decision.decisionId === decisionId
    );

    return decision?.name ?? decision?.decisionId;
  };

  getDecisionDefinitionId = ({
    decisionId,
    version,
  }: {
    decisionId: string;
    version: number;
  }) => {
    return (
      this.decisionVersionsById[decisionId]?.find(
        (decision) => decision.version === version
      )?.id ?? null
    );
  };

  getVersions = (decisionId: string) => {
    return (
      this.decisionVersionsById[decisionId]?.map(({version}) => version) ?? []
    );
  };

  getDefaultVersion = (decisionId: string) => {
    const versions = this.getVersions(decisionId);
    return versions[versions.length - 1];
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
