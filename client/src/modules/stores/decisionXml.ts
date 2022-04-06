/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
import {decisionInstanceDetailsStore} from './decisionInstanceDetails';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';

type State = {
  xml: string | null;
  status: 'initial' | 'fetching' | 'fetched' | 'error';
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
      startFetching: action,
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
          this.fetchDiagramXml(decisionDefinitionId);
        }
      }
    );
  };

  fetchDiagramXml: (decisionDefinitionId: string) => void =
    this.retryOnConnectionLost(async (decisionDefinitionId: string) => {
      this.startFetching();

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
    });

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

  startFetching = () => {
    this.state.status = 'fetching';
  };

  reset() {
    this.disposer?.();
    super.reset();
    this.state = {...DEFAULT_STATE};
  }
}

export const decisionXmlStore = new DecisionXml();
