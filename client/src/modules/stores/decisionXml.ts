/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  makeObservable,
  override,
  action,
  observable,
  reaction,
  IReactionDisposer,
} from 'mobx';
import {fetchDecisionXML} from 'modules/api/decisions';
import {logger} from 'modules/logger';
import {decisionInstanceStore} from './decisionInstance';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';

type State = {
  xml: string | null;
  status: 'initial' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  xml: null,
  status: 'initial',
};

class DecisionXml extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  disposer: IReactionDisposer | null = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
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
          this.fetchDiagramXml(decisionDefinitionId);
        }
      }
    );
  };

  fetchDiagramXml = this.retryOnConnectionLost(
    async (decisionDefinitionId: string) => {
      try {
        const response = await fetchDecisionXML(decisionDefinitionId);

        if (response.ok) {
          this.handleFetchSuccess(await response.text());
        } else {
          this.handleFetchFailure();
        }
      } catch (error) {
        this.handleFetchFailure(error);
      }
    }
  );

  handleFetchSuccess = (xml: string) => {
    this.state.xml = xml;
    this.state.status = 'fetched';
  };

  handleFetchFailure = (error?: unknown) => {
    this.state.status = 'error';

    logger.error('Failed to fetch decision xml');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  reset() {
    this.disposer?.();
    super.reset();
    this.state = {...DEFAULT_STATE};
  }
}

export const decisionXmlStore = new DecisionXml();
