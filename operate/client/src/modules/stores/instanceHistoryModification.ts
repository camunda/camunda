/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';
import type {FlowNodeInstance} from 'modules/types/operate';
import type {FlowNodeModification} from './modifications';

type ModificationPlaceholder = {
  flowNodeInstance: FlowNodeInstance;
  parentFlowNodeId?: string;
  operation: FlowNodeModification['payload']['operation'];
  parentInstanceId?: string;
};

type State = {
  expandedFlowNodeInstanceIds: string[];
};

const DEFAULT_STATE: State = {
  expandedFlowNodeInstanceIds: [],
};

class InstanceHistoryModification {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  addExpandedFlowNodeInstanceIds = (id: FlowNodeInstance['id']) => {
    this.state.expandedFlowNodeInstanceIds.push(id);
  };

  removeFromExpandedFlowNodeInstanceIds = (id: FlowNodeInstance['id']) => {
    this.state.expandedFlowNodeInstanceIds =
      this.state.expandedFlowNodeInstanceIds.filter(
        (expandedFlowNodeInstanceId) => expandedFlowNodeInstanceId !== id,
      );
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const instanceHistoryModificationStore =
  new InstanceHistoryModification();

export type {ModificationPlaceholder};
