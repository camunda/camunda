/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeAutoObservable} from 'mobx';

type FlowNodeModification = {
  operation: 'add' | 'cancel' | 'move';
  flowNode: {id: string; name: string};
  targetFlowNode?: {id: string; name: string};
  affectedTokenCount: number;
};

type VariableModification = {
  operation: 'add' | 'edit';
  flowNode: {id: string; name: string};
  name: string;
  oldValue: string;
  newValue: string;
};

type State = {
  status: 'enabled' | 'moving-token' | 'disabled';
  flowNodeModifications: FlowNodeModification[];
  variableModifications: VariableModification[];
};

const DEFAULT_STATE: State = {
  status: 'disabled',
  flowNodeModifications: [],
  variableModifications: [],
};

class Modifications {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  enableModificationMode = () => {
    this.state.status = 'enabled';
  };

  disableModificationMode = () => {
    this.state.status = 'disabled';
  };

  addFlowNodeModification = (modification: FlowNodeModification) => {
    this.state.flowNodeModifications.push(modification);
  };

  addVariableModification = (modification: VariableModification) => {
    this.state.variableModifications.push(modification);
  };

  removeFlowNodeModification = (flowNodeId: string) => {
    this.state.flowNodeModifications = this.state.flowNodeModifications.filter(
      (modification) => modification.flowNode.id !== flowNodeId
    );
  };

  removeVariableModification = (flowNodeId: string, variableName: string) => {
    this.state.variableModifications = this.state.variableModifications.filter(
      (modification) =>
        !(
          modification.flowNode.id === flowNodeId &&
          modification.name === variableName
        )
    );
  };

  get isModificationModeEnabled() {
    return this.state.status !== 'disabled';
  }

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const modificationsStore = new Modifications();
