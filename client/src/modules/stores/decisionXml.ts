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
import {fetchDecisionXML} from 'modules/api/decisions/decisionXml';
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

      const response = await fetchDecisionXML(decisionDefinitionId);

      if (response.isSuccess) {
        this.handleFetchSuccess(response.data ?? '');
      } else {
        this.handleFetchFailure();
      }
    });

  handleFetchSuccess = (xml: string) => {
    this.state.xml = xml;
    this.state.status = 'fetched';
  };

  handleFetchFailure = () => {
    this.state.status = 'error';
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
