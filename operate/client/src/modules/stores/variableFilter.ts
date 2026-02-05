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

type VariableFilterOperator =
  | 'equals'
  | 'notEqual'
  | 'contains'
  | 'oneOf'
  | 'exists'
  | 'doesNotExist';

type VariableCondition = {
  id: string;
  name: string;
  operator: VariableFilterOperator;
  value: string;
};

type State = {
  conditions: VariableCondition[];
  isInMultipleMode: boolean;
};

const DEFAULT_STATE: State = {
  conditions: [],
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

  setConditions = (conditions: VariableCondition[]) => {
    if (!isEqual(this.state.conditions, conditions)) {
      this.state.conditions = conditions;
      this.state.isInMultipleMode = conditions.length > 1;
    }
  };

  setIsInMultipleMode = (isInMultipleMode: boolean) => {
    this.state.isInMultipleMode = isInMultipleMode;
  };

  get conditions() {
    return this.state.conditions;
  }

  get hasActiveFilters() {
    return this.state.conditions.length > 0;
  }

  /**
   * Backward-compatible getter that derives a single {name, values} variable
   * from the first "equals" condition. Used by the migration flow.
   */
  get variableWithValidatedValues() {
    const equalsCondition = this.state.conditions.find(
      (c) => c.operator === 'equals' && c.name.trim() !== '',
    );

    if (!equalsCondition) {
      return undefined;
    }

    const values =
      getValidVariableValues(equalsCondition.value)?.map((value) =>
        JSON.stringify(value),
      ) ?? [];

    return {
      name: equalsCondition.name,
      values,
    };
  }
}

export const variableFilterStore = new VariableFilter();
export type {VariableCondition, VariableFilterOperator};
