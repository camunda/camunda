/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
import {DiagramModel} from 'bpmn-moddle';
import {fetchProcessXML} from 'modules/api/processes/fetchProcessXML';
import {parseDiagramXML} from 'modules/utils/bpmn';
import {processInstanceDetailsStore} from 'modules/stores/processInstanceDetails';
import {NetworkReconnectionHandler} from '../networkReconnectionHandler';
import {isFlowNode} from 'modules/utils/flowNodes';
import {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {isSubProcess} from 'modules/bpmn-js/utils/isSubProcess';
import {isMultiInstance} from 'modules/bpmn-js/utils/isMultiInstance';

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

class ProcessInstanceDetailsDiagram extends NetworkReconnectionHandler {
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
      businessObjects: computed,
      handleFetchFailure: action,
      reset: override,
    });
  }

  init() {
    this.processXmlDisposer = when(
      () => processInstanceDetailsStore.state.processInstance !== null,
      () => {
        const processId =
          processInstanceDetailsStore.state.processInstance?.processId;

        if (processId !== undefined) {
          this.fetchProcessXml(processId);
        }
      },
    );
  }

  get businessObjects(): BusinessObjects {
    if (this.state.diagramModel === null) {
      return {};
    }

    return Object.entries(this.state.diagramModel.elementsById).reduce(
      (flowNodes, [flowNodeId, businessObject]) => {
        if (isFlowNode(businessObject)) {
          return {...flowNodes, [flowNodeId]: businessObject};
        } else {
          return flowNodes;
        }
      },
      {},
    );
  }

  fetchProcessXml = this.retryOnConnectionLost(
    async (processId: ProcessInstanceEntity['processId']) => {
      this.startFetch();
      const response = await fetchProcessXML(processId);

      if (response.isSuccess) {
        const xml = response.data;
        this.handleFetchSuccess(xml, await parseDiagramXML(xml));
      } else {
        this.handleFetchFailure();
      }
    },
  );

  startFetch = () => {
    if (this.state.status === 'initial') {
      this.state.status = 'first-fetch';
    } else {
      this.state.status = 'fetching';
    }
  };

  handleFetchSuccess = (xml: string, diagramModel: DiagramModel) => {
    this.state.diagramModel = diagramModel;
    this.state.xml = xml;
    this.state.status = 'fetched';
  };

  getFlowNodeName = (flowNodeId: string) => {
    return this.businessObjects[flowNodeId]?.name || flowNodeId;
  };

  isSubProcess = (flowNodeId: string) => {
    const businessObject = this.businessObjects[flowNodeId];
    return isSubProcess(businessObject);
  };

  isMultiInstance = (flowNodeId: string) => {
    const businessObject = this.businessObjects[flowNodeId];
    return isMultiInstance(businessObject);
  };

  handleFetchFailure = (error?: unknown) => {
    this.state.status = 'error';
  };

  reset() {
    super.reset();
    this.state = {...DEFAULT_STATE};
    this.processXmlDisposer?.();
  }
}

export const processInstanceDetailsDiagramStore =
  new ProcessInstanceDetailsDiagram();
