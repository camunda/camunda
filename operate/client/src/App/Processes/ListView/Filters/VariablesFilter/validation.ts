/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {isValidJSON} from 'modules/utils';
import type {VariableCondition} from 'modules/stores/variableFilter';
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
    } else if (condition.operator === 'oneOf') {
      let parsed: unknown;
      try {
        parsed = JSON.parse(condition.value);
      } catch {
        // handled below
      }
      if (!Array.isArray(parsed)) {
        errors.value = 'Value must be a JSON array (e.g. ["val1", "val2"])';
      }
    } else if (
      condition.operator !== 'contains' &&
      !isValidJSON(condition.value)
    ) {
      errors.value = 'Value must be valid JSON';
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
