/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, computed, when} from 'mobx';
import {fetchWorkflowXML} from 'modules/api/diagram';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {createNodeMetaDataMap, getSelectableFlowNodes} from './mappers';
import {currentInstance} from 'modules/stores/currentInstance';

const DEFAULT_STATE = {
  diagramModel: null,
  isInitialLoadComplete: false,
  isLoading: false,
  isFailed: false,
};

class SingleInstanceDiagram {
  state = {...DEFAULT_STATE};

  init() {
    when(
      () => currentInstance.state.instance !== null,
      () => {
        this.fetchWorkflowXml(currentInstance.state.instance.workflowId);
      }
    );
  }

  fetchWorkflowXml = async (workflowId) => {
    this.startLoading();
    const response = await fetchWorkflowXML(workflowId);

    if (response.error) {
      this.handleFailure();
    } else {
      const parsedDiagramXml = await parseDiagramXML(response);
      this.handleSuccess(parsedDiagramXml);
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

  handleSuccess = (parsedDiagramXml) => {
    this.state.diagramModel = parsedDiagramXml;
    this.state.isLoading = false;
    this.state.isFailed = false;
  };

  getMetaData = (activityId) => {
    return this.nodeMetaDataMap.get(activityId);
  };

  get nodeMetaDataMap() {
    return createNodeMetaDataMap(
      getSelectableFlowNodes(this.state.diagramModel?.bpmnElements)
    );
  }

  get areDiagramDefinitionsAvailable() {
    const {isInitialLoadComplete, isFailed, diagramModel} = this.state;
    return isInitialLoadComplete && !isFailed && diagramModel?.definitions;
  }

  handleFailure = () => {
    this.state.isLoading = false;
    this.state.isFailed = true;
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

export const singleInstanceDiagram = new SingleInstanceDiagram();
