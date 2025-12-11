/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import isEqual from 'lodash/isEqual';
import {makeAutoObservable, type IReactionDisposer} from 'mobx';

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

  get variable() {
    return this.state.variable;
  }
}

export const variableFilterStore = new VariableFilter();
export type {Variable};
