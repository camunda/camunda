/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import isEqual from 'lodash/isEqual';
import {makeAutoObservable, runInAction} from 'mobx';

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
  conditions: VariableCondition[];
};

const DEFAULT_STATE: State = {
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
    this.state = {...DEFAULT_STATE};
    sessionStorage.removeItem(SESSION_STORAGE_KEY);
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

  get conditions() {
    return this.state.conditions;
  }

  get hasActiveFilters() {
    return this.state.conditions.length > 0;
  }
}

const variableFilterStore = new VariableFilter();

export {variableFilterStore};
export type {VariableFilterOperator, VariableCondition};
