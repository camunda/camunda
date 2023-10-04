/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import isEqual from 'lodash/isEqual';
import {IReactionDisposer, makeAutoObservable} from 'mobx';

type Variable = {
  name: string;
  values: string;
};

type State = {
  variable?: Variable;
  isInMultipleMode: boolean;
};

const DEFAULT_STATE: State = {
  variable: undefined,
  isInMultipleMode: false,
};

class VariableFilter {
  state: State = {...DEFAULT_STATE};
  disposer: IReactionDisposer | null = null;

  constructor() {
    makeAutoObservable(this);
  }

  reset = () => {
    this.state = {...DEFAULT_STATE};
  };

  setVariable = (variable?: Variable) => {
    if (!isEqual(this.state.variable, variable)) {
      this.state.variable = variable;
    }
  };

  setIsInMultipleMode = (isInMultipleMode: boolean) => {
    this.state.isInMultipleMode = isInMultipleMode;
  };
}

export const variableFilterStore = new VariableFilter();
