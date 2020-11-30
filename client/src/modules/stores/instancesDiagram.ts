/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  makeObservable,
  observable,
  action,
  computed,
  autorun,
  IReactionDisposer,
} from 'mobx';
import {fetchWorkflowXML} from 'modules/api/diagram';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {getFlowNodes} from 'modules/utils/flowNodes';
import {isEmpty} from 'lodash';
import {filtersStore} from 'modules/stores/filters';
import {logger} from 'modules/logger';

type State = {
  diagramModel: unknown;
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  diagramModel: null,
  status: 'initial',
};

class InstancesDiagram {
  state: State = {...DEFAULT_STATE};
  disposer: null | IReactionDisposer = null;

  constructor() {
    makeObservable(this, {
      state: observable,
      reset: action,
      resetDiagramModel: action,
      startFetching: action,
      handleFetchSuccess: action,
      handleFetchError: action,
      selectableFlowNodes: computed,
      selectableIds: computed,
    });
  }

  init = () => {
    this.disposer = autorun(() => {
      if (isEmpty(filtersStore.workflow)) {
        this.resetDiagramModel();
      } else {
        this.fetchWorkflowXml(filtersStore.workflow.id);
      }
    });
  };

  fetchWorkflowXml = async (
    workflowId: WorkflowInstanceEntity['workflowId']
  ) => {
    this.startFetching();
    try {
      const response = await fetchWorkflowXML(workflowId);

      if (response.ok) {
        this.handleFetchSuccess(await parseDiagramXML(await response.text()));
      } else {
        this.handleFetchError();
      }
    } catch (error) {
      this.handleFetchError(error);
    }
  };

  get selectableFlowNodes() {
    // @ts-expect-error ts-migrate(2339) FIXME: Property 'bpmnElements' does not exist on type 'ne... Remove this comment to see the full error message
    return getFlowNodes(this.state.diagramModel?.bpmnElements);
  }

  get selectableIds() {
    // @ts-expect-error ts-migrate(7031) FIXME: Binding element 'id' implicitly has an 'any' type.
    return this.selectableFlowNodes.map(({id}) => id);
  }

  resetDiagramModel = () => {
    this.state.diagramModel = null;
  };

  startFetching = () => {
    this.state.status = 'fetching';
  };

  handleFetchSuccess = (parsedDiagramXml: any) => {
    this.state.diagramModel = parsedDiagramXml;
    this.state.status = 'fetched';
  };

  handleFetchError = (error?: Error) => {
    this.state.status = 'error';

    logger.error('Diagram failed to fetch');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};

    this.disposer?.();
  };
}

export const instancesDiagramStore = new InstancesDiagram();
