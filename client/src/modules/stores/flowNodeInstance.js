/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {observable, decorate, action, computed} from 'mobx';
import {constructFlowNodeIdToFlowNodeInstanceMap} from './mappers';

const DEFAULT_STATE = {
  selection: {
    treeRowIds: [],
    flowNodeId: null,
  },
  flowNodeIdToFlowNodeInstanceMap: new Map(),
};

class FlowNodeInstance {
  state = {...DEFAULT_STATE};

  setCurrentSelection = (selection) => {
    this.state.selection = selection;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };

  setFlowNodeInstanceMap = (flowNodeInstancesTree) => {
    this.state.flowNodeIdToFlowNodeInstanceMap = constructFlowNodeIdToFlowNodeInstanceMap(
      flowNodeInstancesTree
    );
  };

  get areMultipleNodesSelected() {
    return this.state.selection.treeRowIds.length > 1;
  }
}

decorate(FlowNodeInstance, {
  state: observable,
  reset: action,
  setCurrentSelection: action,
  setFlowNodeInstanceMap: action,
  areMultipleNodesSelected: computed,
});

export const flowNodeInstance = new FlowNodeInstance();
