/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeAutoObservable} from 'mobx';
import {generateUniqueID} from 'modules/utils/generateUniqueID';
import {processInstanceDetailsDiagramStore} from './processInstanceDetailsDiagram';
import {isFlowNodeMultiInstance} from './utils/isFlowNodeMultiInstance';

type FlowNodeModificationPayload =
  | {
      operation: 'ADD_TOKEN';
      scopeId: string;
      flowNode: {id: string; name: string};
      affectedTokenCount: number;
      parentScopeIds: {
        [key: string]: string;
      };
    }
  | {
      operation: 'CANCEL_TOKEN';
      flowNode: {id: string; name: string};
      affectedTokenCount: number;
    }
  | {
      operation: 'MOVE_TOKEN';
      flowNode: {id: string; name: string};
      affectedTokenCount: number;
      targetFlowNode: {id: string; name: string};
      scopeIds: string[];
      parentScopeIds: {
        [key: string]: string;
      };
    };

type VariableModificationPayload = {
  operation: 'ADD_VARIABLE' | 'EDIT_VARIABLE';
  id: string;
  scopeId: string;
  flowNodeName: string;
  name: string;
  oldValue?: string;
  newValue: string;
};

type FlowNodeModification = {
  type: 'token';
  payload: FlowNodeModificationPayload;
};

type VariableModification = {
  type: 'variable';
  payload: VariableModificationPayload;
};

type Modification = FlowNodeModification | VariableModification;
type RemovedModificationSource = 'variables' | 'summaryModal' | 'footer';

type State = {
  status: 'enabled' | 'moving-token' | 'disabled' | 'adding-modification';
  modifications: Modification[];
  lastRemovedModification:
    | {
        modification: Modification | undefined;
        source: RemovedModificationSource;
      }
    | undefined;
  sourceFlowNodeIdForMoveOperation: string | null;
};

const DEFAULT_STATE: State = {
  status: 'disabled',
  modifications: [],
  sourceFlowNodeIdForMoveOperation: null,
  lastRemovedModification: undefined,
};

const EMPTY_MODIFICATION = Object.freeze({
  newTokens: 0,
  cancelledTokens: 0,
});

class Modifications {
  state: State = {...DEFAULT_STATE};
  modificationsLoadingTimeout: number | undefined;

  constructor() {
    makeAutoObservable(this);
  }

  startMovingToken = (sourceFlowNodeId: string) => {
    this.state.status = 'moving-token';
    this.state.sourceFlowNodeIdForMoveOperation = sourceFlowNodeId;
  };

  generateParentScopeIds = (targetFlowNodeId: string) => {
    const flowNode =
      processInstanceDetailsDiagramStore.getFlowNode(targetFlowNodeId);

    const parentFlowNodeIds =
      processInstanceDetailsDiagramStore.getFlowNodeParents(flowNode);

    return parentFlowNodeIds.reduce<{[key: string]: string}>(
      (parentFlowNodeScopes, flowNodeId) => {
        parentFlowNodeScopes[flowNodeId] = generateUniqueID();
        return parentFlowNodeScopes;
      },
      {}
    );
  };

  finishMovingToken = (targetFlowNodeId?: string) => {
    if (
      targetFlowNodeId !== undefined &&
      this.state.sourceFlowNodeIdForMoveOperation !== null
    ) {
      const affectedTokenCount = 2; //  TODO: This can only be set when instance counts are known #2926
      const newScopeCount = isFlowNodeMultiInstance(
        this.state.sourceFlowNodeIdForMoveOperation
      )
        ? 1
        : affectedTokenCount;

      modificationsStore.addModification({
        type: 'token',
        payload: {
          operation: 'MOVE_TOKEN',
          flowNode: {
            id: this.state.sourceFlowNodeIdForMoveOperation,
            name: processInstanceDetailsDiagramStore.getFlowNodeName(
              this.state.sourceFlowNodeIdForMoveOperation
            ),
          },
          targetFlowNode: {
            id: targetFlowNodeId,
            name: processInstanceDetailsDiagramStore.getFlowNodeName(
              targetFlowNodeId
            ),
          },
          affectedTokenCount,
          scopeIds: Array.from({
            length: newScopeCount,
          }).map(() => generateUniqueID()),
          parentScopeIds: this.generateParentScopeIds(targetFlowNodeId),
        },
      });
    } else {
      this.state.status = 'enabled';
    }

    this.state.sourceFlowNodeIdForMoveOperation = null;
  };

  enableModificationMode = () => {
    this.state.status = 'enabled';
  };

  disableModificationMode = () => {
    this.state.status = 'disabled';
  };

  addModification = (
    modification: Modification,
    preventLoadingOverlay?: boolean
  ) => {
    if (!preventLoadingOverlay) {
      this.state.status = 'adding-modification';
      this.modificationsLoadingTimeout = window.setTimeout(() => {
        this.enableModificationMode();
      }, 500);
    }

    this.state.modifications.push(modification);
  };

  removeLastModification = () => {
    this.state.lastRemovedModification = {
      modification: this.state.modifications.pop(),
      source: 'footer',
    };
  };

  removeFlowNodeModification = (
    flowNodeModification: FlowNodeModificationPayload
  ) => {
    if (flowNodeModification.operation === 'ADD_TOKEN') {
      this.state.modifications = this.state.modifications.filter(
        ({type, payload}) =>
          !(
            type === 'token' &&
            payload.flowNode.id === flowNodeModification.flowNode.id &&
            payload.operation === flowNodeModification.operation &&
            payload.scopeId === flowNodeModification.scopeId
          )
      );
    } else {
      this.state.modifications = this.state.modifications.filter(
        ({type, payload}) =>
          !(
            type === 'token' &&
            payload.flowNode.id === flowNodeModification.flowNode.id &&
            payload.operation === flowNodeModification.operation
          )
      );
    }
  };

  removeVariableModification = (
    scopeId: string,
    id: string,
    operation: 'ADD_VARIABLE' | 'EDIT_VARIABLE',
    source: RemovedModificationSource
  ) => {
    const lastModification = this.getLastVariableModification(
      scopeId,
      id,
      operation
    );

    if (lastModification === undefined) {
      return;
    }

    const index = this.state.modifications.findIndex(
      ({type, payload}) =>
        type === 'variable' &&
        payload.scopeId === lastModification.scopeId &&
        payload.id === lastModification.id &&
        payload.operation === lastModification.operation
    );

    this.state.lastRemovedModification = {
      modification: this.state.modifications.splice(index, 1)[0],
      source,
    };
  };

  get isModificationModeEnabled() {
    return this.state.status !== 'disabled';
  }

  get lastModification() {
    const [lastModification] = this.state.modifications.slice(-1);

    return lastModification;
  }

  get modificationsByFlowNode() {
    return this.state.modifications.reduce<{
      [key: string]: {
        newTokens: number;
        cancelledTokens: number;
      };
    }>((modificationsByFlowNode, {type, payload}) => {
      if (type === 'variable') {
        return modificationsByFlowNode;
      }
      const {flowNode, operation, affectedTokenCount} = payload;

      if (modificationsByFlowNode[flowNode.id] === undefined) {
        modificationsByFlowNode[flowNode.id] = {...EMPTY_MODIFICATION};
      }

      if (operation === 'MOVE_TOKEN') {
        if (modificationsByFlowNode[payload.targetFlowNode.id] === undefined) {
          modificationsByFlowNode[payload.targetFlowNode.id] = {
            ...EMPTY_MODIFICATION,
          };
        }

        modificationsByFlowNode[flowNode.id]!.cancelledTokens =
          affectedTokenCount;

        const isSourceFlowNodeMultiInstance = isFlowNodeMultiInstance(
          flowNode.id
        );

        modificationsByFlowNode[payload.targetFlowNode.id]!.newTokens =
          isSourceFlowNodeMultiInstance ? 1 : affectedTokenCount;
      }

      if (operation === 'CANCEL_TOKEN') {
        modificationsByFlowNode[flowNode.id]!.cancelledTokens =
          affectedTokenCount;
      }

      if (operation === 'ADD_TOKEN') {
        modificationsByFlowNode[flowNode.id]!.newTokens =
          modificationsByFlowNode[flowNode.id]!.newTokens + affectedTokenCount;
      }

      return modificationsByFlowNode;
    }, {});
  }

  isCancelModificationAppliedOnFlowNode = (flowNodeId: string) => {
    const cancelledTokens =
      this.modificationsByFlowNode[flowNodeId]?.cancelledTokens ?? 0;
    return cancelledTokens > 0;
  };

  get variableModifications() {
    function isVariableModification(
      modification: Modification
    ): modification is VariableModification {
      const {type} = modification;

      return type === 'variable';
    }

    const latestVariableModifications = this.state.modifications
      .filter(isVariableModification)
      .map(({payload}) => payload)
      .reduce<{
        [key: string]: VariableModificationPayload;
      }>((accumulator, modification) => {
        const {id, scopeId} = modification;
        accumulator[`${scopeId}-${id}`] = modification;
        return accumulator;
      }, {});

    return Object.values(latestVariableModifications);
  }

  get flowNodeModifications() {
    function isFlowNodeModification(
      modification: Modification
    ): modification is FlowNodeModification {
      const {type} = modification;

      return type === 'token';
    }

    return this.state.modifications
      .filter(isFlowNodeModification)
      .map(({payload}) => payload);
  }

  getLastVariableModification = (
    flowNodeInstanceId: string | null,
    id: string,
    operation: 'ADD_VARIABLE' | 'EDIT_VARIABLE'
  ) => {
    return this.variableModifications.find(
      (modification) =>
        modification.operation === operation &&
        modification.scopeId === flowNodeInstanceId &&
        modification.id === id
    );
  };

  getAddVariableModifications = (scopeId: string | null) => {
    if (scopeId === null) {
      return [];
    }

    return this.variableModifications
      .filter(
        (modification) =>
          modification.operation === 'ADD_VARIABLE' &&
          modification.scopeId === scopeId
      )
      .map(({name, newValue, id}) => ({
        name,
        value: newValue,
        id,
      }));
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
    window.clearTimeout(this.modificationsLoadingTimeout);
  };
}

export const modificationsStore = new Modifications();
