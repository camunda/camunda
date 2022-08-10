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
  modifications: [
    // TODO: unskip LastModification and modifications store tests when removing these mocks
    {
      type: 'token',
      modification: {
        operation: 'add',
        flowNode: {id: '1', name: 'flowNode1'},
        affectedTokenCount: 1,
      },
    },
    {
      type: 'token',
      modification: {
        operation: 'cancel',
        flowNode: {id: '2', name: 'flowNode2'},
        affectedTokenCount: 2,
      },
    },
    {
      type: 'token',
      modification: {
        operation: 'move',
        flowNode: {id: '3', name: 'flowNode3'},
        targetFlowNode: {id: '4', name: 'flowNode4'},
        affectedTokenCount: 2,
      },
    },
    {
      type: 'variable',
      modification: {
        operation: 'add',
        flowNode: {id: '5', name: 'flowNode5'},
        name: 'variableName1',
        newValue: 'variableValue1',
      },
    },
    {
      type: 'variable',
      modification: {
        operation: 'edit',
        flowNode: {id: '5', name: 'flowNode6'},
        name: 'variableName2',
        oldValue: 'variableValue2',
        newValue: 'editedVariableValue2',
      },
    },
  ],
};

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

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const modificationsStore = new Modifications();
