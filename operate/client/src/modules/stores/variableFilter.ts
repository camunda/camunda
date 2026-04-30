/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import isEqual from 'lodash/isEqual';
import {makeAutoObservable, type IReactionDisposer} from 'mobx';
import {getValidVariableValues} from 'modules/utils/filter/getValidVariableValues';

type Variable = {
  name: string;
  values: string;
};

type State = {
  variables: Variable[];
  isInMultipleMode: boolean;
};

const DEFAULT_STATE: State = {
  variables: [],
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

  setVariables = (variables: Variable[]) => {
    if (!isEqual(this.state.variables, variables)) {
      this.state.variables = variables;
    }
  };

  setVariable = (variable?: Variable) => {
    this.setVariables(variable ? [variable] : []);
  };

  setIsInMultipleMode = (isInMultipleMode: boolean) => {
    this.state.isInMultipleMode = isInMultipleMode;
  };

  get variables() {
    return this.state.variables;
  }

  /** @deprecated Use variables[0] directly */
  get variable() {
    return this.state.variables[0];
  }

  get variableWithValidatedValues() {
    const first = this.state.variables[0];
    if (!first) {
      return undefined;
    }

    const values =
      getValidVariableValues(first.values)?.map((value) =>
        JSON.stringify(value),
      ) ?? [];

    return {
      name: first.name,
      values,
    };
  }
}

export const variableFilterStore = new VariableFilter();
export type {Variable};
