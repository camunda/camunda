/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, computed, when} from 'mobx';
import {fetchWorkflowXML} from 'modules/api/diagram';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {createNodeMetaDataMap, getSelectableFlowNodes} from './mappers';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {logger} from 'modules/logger';

type State = {
  diagramModel: unknown;
  isInitialLoadComplete: boolean;
  isLoading: boolean;
  isFailed: boolean;
};

const DEFAULT_STATE: State = {
  diagramModel: null,
  isInitialLoadComplete: false,
  isLoading: false,
  isFailed: false,
};

class SingleInstanceDiagram {
  state: State = {...DEFAULT_STATE};

  init() {
    when(
      () => currentInstanceStore.state.instance !== null,
      () => {
        this.fetchWorkflowXml(currentInstanceStore.state.instance?.workflowId);
      }
    );
  }

  fetchWorkflowXml = async (workflowId: any) => {
    this.startLoading();

    try {
      const response = await fetchWorkflowXML(workflowId);

      if (response.ok) {
        this.handleSuccess(await parseDiagramXML(await response.text()));
      } else {
        this.handleFailure();
      }
    } catch (error) {
      this.handleFailure(error);
    }

    if (!this.state.isInitialLoadComplete) {
      this.completeInitialLoad();
    }
  };

  completeInitialLoad = () => {
    this.state.isInitialLoadComplete = true;
  };

  startLoading = () => {
    this.state.isLoading = true;
  };

  handleSuccess = (parsedDiagramXml: any) => {
    this.state.diagramModel = parsedDiagramXml;
    this.state.isLoading = false;
    this.state.isFailed = false;
  };

  getMetaData = (activityId: any) => {
    return this.nodeMetaDataMap.get(activityId);
  };

  get nodeMetaDataMap() {
    return createNodeMetaDataMap(
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'bpmnElements' does not exist on type 'ne... Remove this comment to see the full error message
      getSelectableFlowNodes(this.state.diagramModel?.bpmnElements)
    );
  }

  get areDiagramDefinitionsAvailable() {
    const {isInitialLoadComplete, isFailed, diagramModel} = this.state;

    return (
      isInitialLoadComplete &&
      !isFailed &&
      // @ts-expect-error
      diagramModel?.definitions !== undefined
    );
  }

  handleFailure = (error?: Error) => {
    this.state.isLoading = false;
    this.state.isFailed = true;

    logger.error('Failed to fetch single instance diagram');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

decorate(SingleInstanceDiagram, {
  state: observable,
  reset: action,
  startLoading: action,
  handleSuccess: action,
  handleFailure: action,
  completeInitialLoad: action,
  nodeMetaDataMap: computed,
  areDiagramDefinitionsAvailable: computed,
});

export const singleInstanceDiagramStore = new SingleInstanceDiagram();
