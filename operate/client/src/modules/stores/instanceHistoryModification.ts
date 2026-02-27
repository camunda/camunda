/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';
import type {ElementInstancePlaceholder} from 'modules/types/operate';
import type {ElementModification} from './modifications';

type ModificationPlaceholder = {
  elementInstancePlaceholder: ElementInstancePlaceholder;
  parentFlowNodeId?: string;
  operation: ElementModification['payload']['operation'];
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

  addExpandedFlowNodeInstanceIds = (id: ElementInstancePlaceholder['id']) => {
    this.state.expandedFlowNodeInstanceIds.push(id);
  };

  removeFromExpandedFlowNodeInstanceIds = (
    id: ElementInstancePlaceholder['id'],
  ) => {
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
