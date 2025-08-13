/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {makeAutoObservable} from 'mobx';

type State = {
  isEnabled: boolean;
  selectedTargetElementId: string | null;
};

const DEFAULT_STATE: State = {
  isEnabled: false,
  selectedTargetElementId: null,
};

class BatchModification {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  enable = () => {
    this.state.isEnabled = true;
  };

  selectTargetElement = (itemId: State['selectedTargetElementId']) => {
    this.state.selectedTargetElementId = itemId;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const batchModificationStore = new BatchModification();
