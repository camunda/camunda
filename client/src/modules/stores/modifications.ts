/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeAutoObservable} from 'mobx';

type FlowNodeModification =
  | {
      operation: 'add' | 'cancel';
      flowNode: {id: string; name: string};
      affectedTokenCount: number;
    }
  | {
      operation: 'move';
      flowNode: {id: string; name: string};
      affectedTokenCount: number;
      targetFlowNode: {id: string; name: string};
    };

type VariableModification = {
  operation: 'add' | 'edit';
  flowNode: {id: string; name: string};
  name: string;
  oldValue?: string;
  newValue: string;
};

type Modification =
  | {
      type: 'token';
      modification: FlowNodeModification;
    }
  | {
      type: 'variable';
      modification: VariableModification;
    };

type State = {
  status: 'enabled' | 'moving-token' | 'disabled';
  modifications: Modification[];
};

const DEFAULT_STATE: State = {
  status: 'disabled',
  modifications: [],
};

const EMPTY_MODIFICATION = Object.freeze({
  newTokens: 0,
  cancelledTokens: 0,
});

class Modifications {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  startMovingToken = () => {
    this.state.status = 'moving-token';
  };

  finishMovingToken = () => {
    // TODO: #2948: Add flow node modification
    this.state.status = 'enabled';
  };

  enableModificationMode = () => {
    this.state.status = 'enabled';
  };

  disableModificationMode = () => {
    this.state.status = 'disabled';
  };

  addModification = (modification: Modification) => {
    this.state.modifications.push(modification);
  };

  removeLastModification = () => {
    this.state.modifications.pop();
  };

  removeFlowNodeModification = (flowNodeId: string) => {
    this.state.modifications = this.state.modifications.filter(
      ({type, modification}) =>
        !(type === 'token' && modification.flowNode.id === flowNodeId)
    );
  };

  removeVariableModification = (flowNodeId: string, variableName: string) => {
    this.state.modifications = this.state.modifications.filter(
      ({type, modification}) =>
        !(
          type === 'variable' &&
          modification.flowNode.id === flowNodeId &&
          modification.name === variableName
        )
    );
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
    }>((modificationsByFlowNode, {type, modification}) => {
      if (type === 'variable') {
        return modificationsByFlowNode;
      }
      const {flowNode, operation, affectedTokenCount} = modification;

      if (modificationsByFlowNode[flowNode.id] === undefined) {
        modificationsByFlowNode[flowNode.id] = {...EMPTY_MODIFICATION};
      }

      if (operation === 'move') {
        if (
          modificationsByFlowNode[modification.targetFlowNode.id] === undefined
        ) {
          modificationsByFlowNode[modification.targetFlowNode.id] = {
            ...EMPTY_MODIFICATION,
          };
        }

        modificationsByFlowNode[flowNode.id]!.cancelledTokens =
          affectedTokenCount;

        modificationsByFlowNode[modification.targetFlowNode.id]!.newTokens =
          affectedTokenCount;
      }

      if (operation === 'cancel') {
        modificationsByFlowNode[flowNode.id]!.cancelledTokens =
          affectedTokenCount;
      }

      if (operation === 'add') {
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

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const modificationsStore = new Modifications();
