/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {makeAutoObservable, when, IReactionDisposer} from 'mobx';
import {fetchWorkflowXML} from 'modules/api/diagram';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {createNodeMetaDataMap, getSelectableFlowNodes} from './mappers';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {logger} from 'modules/logger';

type FlowNodeMetaData = {
  name: string;
  type: {
    elementType: string;
    multiInstanceType?: string;
    eventType?: string;
  };
};

type State = {
  diagramModel: unknown;
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'error';
  nodeMetaDataMap?: Map<string, FlowNodeMetaData>;
};

const DEFAULT_STATE: State = {
  diagramModel: null,
  status: 'initial',
  nodeMetaDataMap: undefined,
};

class SingleInstanceDiagram {
  state: State = {
    ...DEFAULT_STATE,
  };
  workflowXmlDisposer: null | IReactionDisposer = null;

  constructor() {
    makeAutoObservable(this);
  }

  init() {
    this.workflowXmlDisposer = when(
      () => currentInstanceStore.state.instance !== null,
      () => {
        const workflowId = currentInstanceStore.state.instance?.workflowId;

        if (workflowId !== undefined) {
          this.fetchWorkflowXml(workflowId);
        }
      }
    );
  }

  fetchWorkflowXml = async (
    workflowId: WorkflowInstanceEntity['workflowId']
  ) => {
    this.startFetch();
    try {
      const response = await fetchWorkflowXML(workflowId);

      if (response.ok) {
        this.handleFetchSuccess(await parseDiagramXML(await response.text()));
      } else {
        this.handleFetchFailure();
      }
    } catch (error) {
      this.handleFetchFailure(error);
    }
  };

  startFetch = () => {
    if (this.state.status === 'initial') {
      this.state.status = 'first-fetch';
    } else {
      this.state.status = 'fetching';
    }
  };

  handleFetchSuccess = (parsedDiagramXml: any) => {
    this.state.diagramModel = parsedDiagramXml;
    this.state.nodeMetaDataMap = createNodeMetaDataMap(
      getSelectableFlowNodes(parsedDiagramXml.bpmnElements)
    );
    this.state.status = 'fetched';
  };

  getMetaData = (flowNodeInstanceId: string | null) => {
    if (flowNodeInstanceId === null) {
      return;
    }
    return this.state.nodeMetaDataMap?.get(flowNodeInstanceId);
  };

  get areDiagramDefinitionsAvailable() {
    const {status, diagramModel} = this.state;

    return (
      status === 'fetched' &&
      // @ts-expect-error
      diagramModel?.definitions !== undefined
    );
  }

  handleFetchFailure = (error?: Error) => {
    this.state.status = 'error';

    logger.error('Failed to fetch Diagram XML');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
    this.workflowXmlDisposer?.();
  };
}

export type {FlowNodeMetaData};
export const singleInstanceDiagramStore = new SingleInstanceDiagram();
