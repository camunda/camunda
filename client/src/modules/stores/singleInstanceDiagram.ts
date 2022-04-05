/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  makeObservable,
  when,
  IReactionDisposer,
  action,
  computed,
  override,
  observable,
} from 'mobx';
import {fetchProcessXML} from 'modules/api/diagram';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {
  createNodeMetaDataMap,
  getSelectableFlowNodes,
  NodeMetaDataMap,
} from './mappers';
import {currentInstanceStore} from 'modules/stores/currentInstance';
import {logger} from 'modules/logger';
import {NetworkReconnectionHandler} from './networkReconnectionHandler';

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
  xml: string | null;
  status: 'initial' | 'first-fetch' | 'fetching' | 'fetched' | 'error';
  nodeMetaDataMap?: NodeMetaDataMap;
};

const DEFAULT_STATE: State = {
  diagramModel: null,
  xml: null,
  status: 'initial',
  nodeMetaDataMap: undefined,
};

class SingleInstanceDiagram extends NetworkReconnectionHandler {
  state: State = {
    ...DEFAULT_STATE,
  };
  processXmlDisposer: null | IReactionDisposer = null;

  constructor() {
    super();
    makeObservable(this, {
      state: observable,
      startFetch: action,
      handleFetchSuccess: action,
      areDiagramDefinitionsAvailable: computed,
      hasCalledInstances: computed,
      handleFetchFailure: action,
      reset: override,
    });
  }

  init() {
    this.processXmlDisposer = when(
      () => currentInstanceStore.state.instance !== null,
      () => {
        const processId = currentInstanceStore.state.instance?.processId;

        if (processId !== undefined) {
          this.fetchProcessXml(processId);
        }
      }
    );
  }

  fetchProcessXml = this.retryOnConnectionLost(
    async (processId: ProcessInstanceEntity['processId']) => {
      this.startFetch();
      try {
        const response = await fetchProcessXML(processId);

        if (response.ok) {
          const xml = await response.text();
          this.handleFetchSuccess(xml, await parseDiagramXML(xml));
        } else {
          this.handleFetchFailure();
        }
      } catch (error) {
        this.handleFetchFailure(error);
      }
    }
  );

  startFetch = () => {
    if (this.state.status === 'initial') {
      this.state.status = 'first-fetch';
    } else {
      this.state.status = 'fetching';
    }
  };

  handleFetchSuccess = (xml: string, parsedDiagramXml: any) => {
    this.state.diagramModel = parsedDiagramXml;
    this.state.xml = xml;
    this.state.nodeMetaDataMap = createNodeMetaDataMap(
      getSelectableFlowNodes(parsedDiagramXml.bpmnElements)
    );
    this.state.status = 'fetched';
  };

  getMetaData = (flowNodeId: string | null) => {
    if (flowNodeId === null) {
      return;
    }
    return this.state.nodeMetaDataMap?.[flowNodeId];
  };

  getFlowNodeName = (flowNodeId: string) => {
    return this.getMetaData(flowNodeId)?.name || flowNodeId;
  };

  get areDiagramDefinitionsAvailable() {
    const {status, diagramModel} = this.state;

    return (
      status === 'fetched' &&
      // @ts-expect-error
      diagramModel?.definitions !== undefined
    );
  }

  get hasCalledInstances() {
    const {nodeMetaDataMap} = this.state;

    if (nodeMetaDataMap === undefined) {
      return false;
    }

    return Object.values(nodeMetaDataMap).some(({type}) => {
      return type.elementType === 'TASK_CALL_ACTIVITY';
    });
  }

  handleFetchFailure = (error?: unknown) => {
    this.state.status = 'error';

    logger.error('Failed to fetch Diagram XML');
    if (error !== undefined) {
      logger.error(error);
    }
  };

  reset() {
    super.reset();
    this.state = {...DEFAULT_STATE};
    this.processXmlDisposer?.();
  }
}

export type {FlowNodeMetaData};
export const singleInstanceDiagramStore = new SingleInstanceDiagram();
