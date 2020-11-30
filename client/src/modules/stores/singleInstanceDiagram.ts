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
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  diagramModel: null,
  status: 'initial',
};

class SingleInstanceDiagram {
  state: State = {
    ...DEFAULT_STATE,
  };

  init() {
    when(
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

  handleFetchSuccess = (parsedDiagramXml: unknown) => {
    this.state.diagramModel = parsedDiagramXml;
    this.state.status = 'fetched';
  };

  getMetaData = (activityId: string | null | undefined) => {
    return this.nodeMetaDataMap.get(activityId);
  };

  get nodeMetaDataMap() {
    return createNodeMetaDataMap(
      // @ts-expect-error ts-migrate(2339) FIXME: Property 'bpmnElements' does not exist on type 'ne... Remove this comment to see the full error message
      getSelectableFlowNodes(this.state.diagramModel?.bpmnElements)
    );
  }

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
  };
}

decorate(SingleInstanceDiagram, {
  state: observable,
  reset: action,
  startFetch: action,
  handleFetchSuccess: action,
  handleFetchFailure: action,
  nodeMetaDataMap: computed,
  areDiagramDefinitionsAvailable: computed,
});

export const singleInstanceDiagramStore = new SingleInstanceDiagram();
