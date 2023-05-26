/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {Variable} from 'modules/types';

const variables: Variable[] = [
  {
    id: '0001',
    name: 'myVar',
    value: '"0001"',
    previewValue: '"0001"',
    isValueTruncated: false,
  },
  {
    id: '0002',
    name: 'isCool',
    value: '"yes"',
    previewValue: '"yes"',
    isValueTruncated: false,
  },
];

const dynamicFormVariables: Variable[] = [
  {
    id: '0001',
    name: 'radio_field',
    value: '"radio_value_1"',
    previewValue: '"radio_value_1"',
    isValueTruncated: false,
  },
  {
    id: '0002',
    name: 'radio_field_options',
    value:
      '[{"label":"Radio label 1","value":"radio_value_1"},{"label":"Radio label 2","value":"radio_value_2"}]',
    previewValue:
      '[{"label":"Radio label 1","value":"radio_value_1"},{"label":"Radio label 2","value":"radio_value_2"}]',
    isValueTruncated: false,
  },
];

const truncatedVariables: Variable[] = [
  {
    id: '0-myVar',
    name: 'myVar',
    previewValue: '"000',
    value: '"000',
    isValueTruncated: true,
  },
  {
    id: '1-myVar',
    name: 'myVar1',
    previewValue: '"111',
    value: '"111',
    isValueTruncated: true,
  },
];

const fullVariable = (variable: Partial<Variable> = {}): Variable => {
  const baseVariable = {
    id: '0-myVar',
    name: 'myVar',
    previewValue: '"0001"',
    value: '"0001"',
    isValueTruncated: true,
  };
  return {
    ...baseVariable,
    ...variable,
  };
};

export {variables, dynamicFormVariables, truncatedVariables, fullVariable};
