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

type VariableConditionWithValue = {
  name: string;
  operator: 'equals' | 'notEqual' | 'contains' | 'oneOf';
  value: string;
};

type VariableConditionWithoutValue = {
  name: string;
  operator: 'exists' | 'doesNotExist';
  value: '';
};

type VariableCondition =
  | VariableConditionWithValue
  | VariableConditionWithoutValue;

type State = {
  variables: Variable[];
  isInMultipleMode: boolean;
  conditions: VariableCondition[];
};

const DEFAULT_STATE: State = {
  variables: [],
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
        // TODO check if this can be removed after a while, added just to be safe in case of invalid data in session storage
      }
    }
  }

  reset = () => {
    this.state = {...DEFAULT_STATE};
    sessionStorage.removeItem(SESSION_STORAGE_KEY);
  };

  setVariables = (variables: Variable[]) => {
    if (!isEqual(this.state.variables, variables)) {
      this.state.variables = variables;
    }
  };

  setVariable = (variable?: Variable) => {
    this.setVariables(variable ? [variable] : []);
  };

  setConditions = (conditions: VariableCondition[]) => {
    if (!isEqual(this.state.conditions, conditions)) {
      this.state.conditions = conditions;

      if (conditions.length === 0) {
        sessionStorage.removeItem(SESSION_STORAGE_KEY);
      } else {
        sessionStorage.setItem(
          SESSION_STORAGE_KEY,
          JSON.stringify(conditions),
        );
      }
    }
  };

  get variables() {
    return this.state.variables;
  }

  /** @deprecated Use variables[0] directly */
  get variable() {
    return this.state.variables[0];
  }

  get conditions() {
    return this.state.conditions;
  }

  get hasActiveFilters() {
    return this.state.conditions.length > 0;
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

const variableFilterStore = new VariableFilter();

export {variableFilterStore};
export type {Variable, VariableFilterOperator, VariableCondition};