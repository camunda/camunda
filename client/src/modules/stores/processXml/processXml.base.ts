/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeObservable, action, observable, override, computed} from 'mobx';
import {DiagramModel} from 'bpmn-moddle';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from '../networkReconnectionHandler';
import {fetchProcessXML} from 'modules/api/processes/fetchProcessXML';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {getFlowNodes} from 'modules/utils/flowNodes';

type State = {
  diagramModel: DiagramModel | null;
  xml: string | null;
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'error';
};

const DEFAULT_STATE: State = {
  diagramModel: null,
  xml: null,
  status: 'initial',
};

class ProcessXmlBase extends NetworkReconnectionHandler {
  state: State = {...DEFAULT_STATE};
  processId: ProcessInstanceEntity['processId'] | null = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      reset: override,
      startFetching: action,
      handleFetchXmlSuccess: action,
      handleFetchError: action,
      selectableFlowNodes: computed,
      selectableIds: computed,
      resetState: action,
    });
  }

  fetchProcessXml = this.retryOnConnectionLost(
    async (processId: ProcessInstanceEntity['processId']) => {
      if (this.processId === processId) {
        return;
      }

      this.startFetching();
      const processXMLResponse = await fetchProcessXML(processId);

      if (processXMLResponse.isSuccess) {
        this.processId = processId;
        const xml = processXMLResponse.data;
        const diagramModel = await parseDiagramXML(xml);
        this.handleFetchXmlSuccess(xml, diagramModel);
      } else {
        this.handleFetchError();
      }
    },
  );

  get selectableFlowNodes() {
    return getFlowNodes(this.state.diagramModel?.elementsById);
  }

  get selectableIds() {
    return this.selectableFlowNodes.map(({id}) => id);
  }

  startFetching = () => {
    this.state.status = 'fetching';
  };

  handleFetchXmlSuccess = (xml: string, diagramModel: DiagramModel) => {
    this.state.xml = xml;
    this.state.diagramModel = diagramModel;
    this.state.status = 'fetched';
  };

  handleFetchError = (error?: unknown) => {
    this.state.xml = null;
    this.state.diagramModel = null;
    this.state.status = 'error';

    logger.error('Failed to fetch diagram xml');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  get flowNodeFilterOptions() {
    return this.selectableFlowNodes
      .map(({id, name}) => ({
        value: id,
        label: name ?? id,
      }))
      .sort((node, nextNode) => {
        const label = node.label.toUpperCase();
        const nextLabel = nextNode.label.toUpperCase();

        if (label < nextLabel) {
          return -1;
        }
        if (label > nextLabel) {
          return 1;
        }

        return 0;
      });
  }

  resetState = () => {
    this.state = {...DEFAULT_STATE};
  };

  reset() {
    this.processId = null;
    super.reset();
    this.resetState();
  }
}

export {ProcessXmlBase};
