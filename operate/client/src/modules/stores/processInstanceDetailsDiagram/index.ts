/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
import {NON_APPENDABLE_FLOW_NODES} from 'modules/constants';
import {modificationsStore} from '../modifications';
import {isAttachedToAnEventBasedGateway} from 'modules/bpmn-js/utils/isAttachedToAnEventBasedGateway';
import {processInstanceDetailsStatisticsStore} from '../processInstanceDetailsStatistics';
import {isWithinMultiInstance} from 'modules/bpmn-js/utils/isWithinMultiInstance';
import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {isSubProcess} from 'modules/bpmn-js/utils/isSubProcess';
import {isMultiInstance} from 'modules/bpmn-js/utils/isMultiInstance';
import {IS_ADD_TOKEN_WITH_ANCESTOR_KEY_SUPPORTED} from 'modules/feature-flags';

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
      areDiagramDefinitionsAvailable: computed,
      hasCalledProcessInstances: computed,
      flowNodes: computed,
      businessObjects: computed,
      processBusinessObject: computed,
      cancellableFlowNodes: computed,
      appendableFlowNodes: computed,
      modifiableFlowNodes: computed,
      nonModifiableFlowNodes: computed,
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

  get businessObjects(): {[flowNodeId: string]: BusinessObject} {
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

  get processBusinessObject(): BusinessObject | undefined {
    const bpmnProcessId =
      processInstanceDetailsStore.state.processInstance?.bpmnProcessId;

    if (bpmnProcessId === undefined) {
      return undefined;
    }

    return this.state.diagramModel?.elementsById[bpmnProcessId];
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

  getFlowNodeParents = (flowNodeId: string): string[] => {
    const bpmnProcessId =
      processInstanceDetailsStore.state.processInstance?.bpmnProcessId;

    if (bpmnProcessId === undefined) {
      return [];
    }

    return this.getFlowNodesInBetween(flowNodeId, bpmnProcessId);
  };

  getFlowNodesInBetween = (
    fromFlowNodeId: string,
    toFlowNodeId: string,
  ): string[] => {
    const fromFlowNode =
      processInstanceDetailsDiagramStore.businessObjects[fromFlowNodeId];

    if (
      fromFlowNode?.$parent === undefined ||
      fromFlowNode.$parent.id === toFlowNodeId
    ) {
      return [];
    }

    return [
      fromFlowNode.$parent.id,
      ...this.getFlowNodesInBetween(fromFlowNode.$parent.id, toFlowNodeId),
    ];
  };

  hasMultipleScopes = (parentFlowNode?: BusinessObject): boolean => {
    if (parentFlowNode === undefined) {
      return false;
    }

    const scopeCount =
      processInstanceDetailsStatisticsStore.getTotalRunningInstancesForFlowNode(
        parentFlowNode.id,
      );

    if (scopeCount > 1) {
      return true;
    }

    if (parentFlowNode.$parent?.$type !== 'bpmn:SubProcess') {
      return false;
    }

    return this.hasMultipleScopes(parentFlowNode.$parent);
  };

  get flowNodes() {
    return Object.values(this.businessObjects).map((flowNode) => {
      const flowNodeState =
        processInstanceDetailsStatisticsStore.state.statistics.find(
          ({activityId}) => activityId === flowNode.id,
        );

      return {
        id: flowNode.id,
        isCancellable:
          flowNodeState !== undefined &&
          (flowNodeState.active > 0 || flowNodeState.incidents > 0),
        isAppendable: !NON_APPENDABLE_FLOW_NODES.includes(flowNode.$type),
        hasMultiInstanceParent: isWithinMultiInstance(flowNode),
        isAttachedToAnEventBasedGateway:
          isAttachedToAnEventBasedGateway(flowNode),
        hasMultipleScopes: this.hasMultipleScopes(flowNode.$parent),
      };
    });
  }

  get appendableFlowNodes() {
    return this.flowNodes
      .filter(
        (flowNode) =>
          !flowNode.hasMultiInstanceParent &&
          !flowNode.isAttachedToAnEventBasedGateway &&
          flowNode.isAppendable &&
          ((IS_ADD_TOKEN_WITH_ANCESTOR_KEY_SUPPORTED &&
            modificationsStore.state.status !== 'moving-token') ||
            !flowNode.hasMultipleScopes),
      )
      .map(({id}) => id);
  }

  get cancellableFlowNodes() {
    return this.flowNodes
      .filter((flowNode) => flowNode.isCancellable)
      .map(({id}) => id);
  }

  get modifiableFlowNodes() {
    if (modificationsStore.state.status === 'moving-token') {
      return this.appendableFlowNodes.filter(
        (flowNodeId) =>
          flowNodeId !==
          modificationsStore.state.sourceFlowNodeIdForMoveOperation,
      );
    } else {
      return Array.from(
        new Set([...this.appendableFlowNodes, ...this.cancellableFlowNodes]),
      );
    }
  }

  get nonModifiableFlowNodes() {
    return this.flowNodes
      .filter((flowNode) => !this.modifiableFlowNodes.includes(flowNode.id))
      .map(({id}) => id);
  }

  get areDiagramDefinitionsAvailable() {
    const {status, diagramModel} = this.state;
    return status === 'fetched' && diagramModel !== null;
  }

  get hasCalledProcessInstances() {
    return Object.values(this.businessObjects).some(
      ({$type}) => $type === 'bpmn:CallActivity',
    );
  }

  isSubProcess = (flowNodeId: string) => {
    const businessObject = this.businessObjects[flowNodeId];
    return isSubProcess(businessObject);
  };

  isMultiInstance = (flowNodeId: string) => {
    const businessObject = this.businessObjects[flowNodeId];
    return isMultiInstance(businessObject);
  };

  getParentFlowNode = (flowNodeId: string) => {
    const businessObject = this.businessObjects[flowNodeId];
    return businessObject?.$parent;
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
