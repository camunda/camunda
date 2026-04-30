/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import isEqual from 'lodash/isEqual';
import {makeAutoObservable, runInAction} from 'mobx';
import {getValidVariableValues} from 'modules/utils/filter/getValidVariableValues';

type Variable = {
  name: string;
  values: string;
};

type VariableFilterOperator =
  | 'equals'
  | 'notEqual'
  | 'contains'
  | 'oneOf'
  | 'exists'
  | 'doesNotExist';

type VariableCondition = {
  name: string;
  operator: VariableFilterOperator;
  value: string;
};

type State = {
  variable?: Variable;
  isInMultipleMode: boolean;
  conditions: VariableCondition[];
};

const DEFAULT_STATE: State = {
  variable: undefined,
  isInMultipleMode: false,
  conditions: [],
};

const SESSION_STORAGE_KEY = 'operate.variableFilter.conditions';

class VariableFilter {
  state: State = {...DEFAULT_STATE};

  constructor() {
    makeAutoObservable(this);
    const stored = sessionStorage.getItem(SESSION_STORAGE_KEY);
    if (stored) {
      try {
        runInAction(() => {
          this.state.conditions = JSON.parse(stored) as VariableCondition[];
        });
      } catch {
        //TODO check if this can be removed after a while, added just to be safe in case of invalid data in session storage
      }
    }
  }

  reset = () => {
    this.state.variable = DEFAULT_STATE.variable;
    this.state.isInMultipleMode = DEFAULT_STATE.isInMultipleMode;
    this.state.conditions = DEFAULT_STATE.conditions;
    sessionStorage.removeItem(SESSION_STORAGE_KEY);
  };

  setVariable = (variable?: Variable) => {
    if (!isEqual(this.state.variable, variable)) {
      this.state.variable = variable;
    }
  };

  setIsInMultipleMode = (isInMultipleMode: boolean) => {
    this.state.isInMultipleMode = isInMultipleMode;
  };

  setConditions = (conditions: VariableCondition[]) => {
    if (!isEqual(this.state.conditions, conditions)) {
      this.state.conditions = conditions;
      if (conditions.length === 0) {
        sessionStorage.removeItem(SESSION_STORAGE_KEY);
      } else {
        sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(conditions));
      }
    }
  };

  get variable() {
    return this.state.variable;
  }

  get conditions() {
    return this.state.conditions;
  }

  get hasActiveFilters() {
    return this.state.conditions.length > 0;
  }

  get variableWithValidatedValues() {
    if (!this.state.variable) {
      return undefined;
    }

    const values =
      getValidVariableValues(this.state.variable.values)?.map((value) =>
        JSON.stringify(value),
      ) ?? [];

    return {
      name: this.state.variable.name,
      values,
    };
  }
}

const variableFilterStore = new VariableFilter();

export {variableFilterStore};
export type {Variable, VariableFilterOperator, VariableCondition};
