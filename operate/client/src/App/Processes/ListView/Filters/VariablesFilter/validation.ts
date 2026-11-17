/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {VariableCondition} from 'modules/stores/variableFilter';
import {smartTransformValue} from 'modules/utils/smartTransform';
import type {DraftCondition} from './constants';

type RowErrors = {
  name?: string;
  value?: string;
};

const validateCondition = (condition: DraftCondition): RowErrors => {
  const errors: RowErrors = {};

  if (!condition.name?.trim()) {
    errors.name = 'Variable name is required';
  }

  if (
    condition.operator !== 'exists' &&
    condition.operator !== 'doesNotExist'
  ) {
    if (!condition.value?.trim()) {
      errors.value = 'Value is required';
    } else if (condition.operator !== 'contains') {
      // `contains` skips value-shape validation — its value is a raw
      // substring passed straight through to $like. See smartTransform.ts.
      try {
        smartTransformValue(condition.value);
      } catch (e) {
        errors.value = e instanceof Error ? e.message : 'Invalid value';
      }
    }
  }

  return errors;
};

const hasErrors = (errors: RowErrors) => Object.keys(errors).length > 0;

const mapToVariableCondition = (c: DraftCondition): VariableCondition => {
  if (c.operator === 'exists' || c.operator === 'doesNotExist') {
    return {name: c.name, operator: c.operator, value: ''};
  }
  return {name: c.name, operator: c.operator, value: c.value ?? ''};
};

export {validateCondition, hasErrors, mapToVariableCondition, type RowErrors};
