/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {IReactionDisposer, makeAutoObservable, when} from 'mobx';
import {FlowNodeInstance} from './flowNodeInstance';
import {currentInstanceStore} from 'modules/stores/currentInstance';

type Selection = {
  flowNodeId?: string;
  flowNodeInstanceId?: FlowNodeInstance['id'];
  flowNodeType?: string;
  isMultiInstance?: boolean;
};

type State = {
  selection: Selection | null;
};

const DEFAULT_STATE: State = {
  selection: null,
};

class FlowNodeSelection {
  state: State = {...DEFAULT_STATE};
  rootNodeSelectionDisposer: null | IReactionDisposer = null;

  constructor() {
    makeAutoObservable(this, {init: false, selectFlowNode: false});
  }

  init = () => {
    this.rootNodeSelectionDisposer = when(
      () => currentInstanceStore.state.instance?.id !== undefined,
      () => this.setSelection(this.rootNode)
    );
  };

  setSelection = (selection: Selection | null) => {
    this.state.selection = selection;
  };

  selectFlowNode = (selection: Selection) => {
    if (
      selection.flowNodeId === undefined ||
      (!this.areMultipleInstancesSelected && this.isSelected(selection))
    ) {
      this.setSelection(this.rootNode);
    } else {
      this.setSelection(selection);
    }
  };

  get areMultipleInstancesSelected(): boolean {
    if (this.state.selection === null) {
      return false;
    }

    const {flowNodeInstanceId, flowNodeId} = this.state.selection;
    return flowNodeId !== undefined && flowNodeInstanceId === undefined;
  }

  get rootNode() {
    return {
      flowNodeInstanceId: currentInstanceStore.state.instance?.id,
      isMultiInstance: false,
    };
  }

  isSelected = ({
    flowNodeId,
    flowNodeInstanceId,
    isMultiInstance,
  }: {
    flowNodeId?: string;
    flowNodeInstanceId?: string;
    isMultiInstance?: boolean;
  }) => {
    const {selection} = this.state;

    if (selection === null) {
      return false;
    }

    if (selection.isMultiInstance !== isMultiInstance) {
      return false;
    }

    if (selection.flowNodeInstanceId === undefined) {
      return selection.flowNodeId === flowNodeId;
    }

    return selection.flowNodeInstanceId === flowNodeInstanceId;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
    this.rootNodeSelectionDisposer?.();
  };
}

export const flowNodeSelectionStore = new FlowNodeSelection();
export type {Selection};
