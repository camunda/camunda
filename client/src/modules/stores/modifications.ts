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
