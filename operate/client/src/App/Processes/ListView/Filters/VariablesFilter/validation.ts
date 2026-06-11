/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {isValidJSON} from 'modules/utils';
import type {VariableCondition} from 'modules/stores/variableFilter';
import {IS_VARIABLE_FILTER_V2_ENABLED} from 'modules/feature-flags';
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
      const error = IS_VARIABLE_FILTER_V2_ENABLED
        ? validateSmartValue(condition.value)
        : validateLegacyValue(condition.operator, condition.value);
      if (error !== undefined) {
        errors.value = error;
      }
    }
  }

  return errors;
};

const validateSmartValue = (value: string): string | undefined => {
  try {
    smartTransformValue(value);
    return undefined;
  } catch (e) {
    return e instanceof Error ? e.message : 'Invalid value';
  }
};

const validateLegacyValue = (
  operator: Exclude<
    DraftCondition['operator'],
    'exists' | 'doesNotExist' | 'contains'
  >,
  value: string,
): string | undefined => {
  if (operator === 'oneOf') {
    let parsed: unknown;
    try {
      parsed = JSON.parse(value);
    } catch {
      // handled below
    }
    if (!Array.isArray(parsed)) {
      return 'Value must be a JSON array (e.g. ["val1", "val2"])';
    }
    return undefined;
  }
  return isValidJSON(value) ? undefined : 'Value must be valid JSON';
};

const hasErrors = (errors: RowErrors) => Object.keys(errors).length > 0;

const mapToVariableCondition = (c: DraftCondition): VariableCondition => {
  if (c.operator === 'exists' || c.operator === 'doesNotExist') {
    return {name: c.name, operator: c.operator, value: ''};
  }
  return {name: c.name, operator: c.operator, value: c.value ?? ''};
};

export {validateCondition, hasErrors, mapToVariableCondition, type RowErrors};
