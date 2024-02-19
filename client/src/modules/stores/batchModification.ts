/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeAutoObservable} from 'mobx';

type State = {
  isEnabled: boolean;
};

const DEFAULT_STATE: State = {
  isEnabled: false,
};

class BatchModification {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  enable = () => {
    this.state.isEnabled = true;
  };

  disable = () => {
    this.state.isEnabled = false;
  };

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const batchModificationStore = new BatchModification();
