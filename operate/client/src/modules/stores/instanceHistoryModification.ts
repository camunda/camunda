/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';
import type {ProcessInstanceState} from '@camunda/camunda-api-zod-schemas/8.10';
import type {ElementModification} from './modifications';

type ElementInstancePlaceholder = {
  id: string;
  type: string;
  state?: ProcessInstanceState;
  flowNodeId: string;
  startDate: string;
  endDate: null | string;
  treePath: string;
  sortValues: [string, string] | [];
  isPlaceholder?: boolean;
};

type ModificationPlaceholder = {
  elementInstancePlaceholder: ElementInstancePlaceholder;
  parentElementId?: string;
  operation: ElementModification['payload']['operation'];
  parentInstanceId?: string;
};

type State = {
  expandedElementInstanceIds: string[];
};

const DEFAULT_STATE: State = {
  expandedElementInstanceIds: [],
};

class InstanceHistoryModification {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  addExpandedElementInstanceIds = (id: ElementInstancePlaceholder['id']) => {
    this.state.expandedElementInstanceIds.push(id);
  };

  removeFromExpandedElementInstanceIds = (
    id: ElementInstancePlaceholder['id'],
  ) => {
    this.state.expandedElementInstanceIds =
      this.state.expandedElementInstanceIds.filter(
        (expandedElementInstanceId) => expandedElementInstanceId !== id,
      );
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const instanceHistoryModificationStore =
  new InstanceHistoryModification();

export type {ElementInstancePlaceholder, ModificationPlaceholder};
