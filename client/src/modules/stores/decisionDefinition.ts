/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {makeAutoObservable} from 'mobx';

type State = {
  definition: {name: string; id: string} | null;
};

const DEFAULT_STATE: State = {
  definition: null,
};

class DecisionDefinition {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
  }

  setDefinition = (definition?: State['definition']) => {
    this.state.definition = definition ?? null;
  };

  get name() {
    return this.state.definition?.name ?? this.state.definition?.id;
  }

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };
}

export const decisionDefinitionStore = new DecisionDefinition();
