/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  QueryVariablesByUserTaskResponseBody,
  Variable,
} from '@camunda/camunda-api-zod-schemas/8.9';
import {DEFAULT_TENANT_ID} from 'common/multitenancy/constants';

const variables: Variable[] = [
  {
    variableKey: '0001',
    name: 'myVar',
    value: '"0001"',
    isTruncated: false,
    tenantId: DEFAULT_TENANT_ID,
    scopeKey: '0001',
    processInstanceKey: '0001',
    rootProcessInstanceKey: null,
  },
  {
    variableKey: '0002',
    name: 'isCool',
    value: '"yes"',
    isTruncated: false,
    tenantId: DEFAULT_TENANT_ID,
    scopeKey: '0002',
    processInstanceKey: '0002',
    rootProcessInstanceKey: null,
  },
];

const dynamicFormVariables: Variable[] = [
  {
    variableKey: '0001',
    name: 'radio_field',
    value: '"radio_value_1"',
    isTruncated: false,
    tenantId: DEFAULT_TENANT_ID,
    scopeKey: '0001',
    processInstanceKey: '0001',
    rootProcessInstanceKey: null,
  },
  {
    variableKey: '0002',
    name: 'radio_field_options',
    value:
      '[{"label":"Radio label 1","value":"radio_value_1"},{"label":"Radio label 2","value":"radio_value_2"}]',
    isTruncated: false,
    tenantId: DEFAULT_TENANT_ID,
    scopeKey: '0002',
    processInstanceKey: '0002',
    rootProcessInstanceKey: null,
  },
];

const truncatedVariables: Variable[] = [
  {
    variableKey: '0-myVar',
    name: 'myVar',
    value: '"000',
    isTruncated: true,
    tenantId: DEFAULT_TENANT_ID,
    scopeKey: '0-myVar',
    processInstanceKey: '0001',
    rootProcessInstanceKey: null,
  },
  {
    variableKey: '1-myVar',
    name: 'myVar1',
    value: '"111',
    isTruncated: true,
    tenantId: DEFAULT_TENANT_ID,
    scopeKey: '1-myVar',
    processInstanceKey: '0002',
    rootProcessInstanceKey: null,
  },
];

const fullVariable = (
  variable: Partial<Pick<Variable, 'variableKey' | 'name' | 'value'>> = {},
): Pick<Variable, 'variableKey' | 'name' | 'value'> => {
  const baseVariable = {
    variableKey: '0-myVar',
    name: 'myVar',
    value: '"0001"',
    isTruncated: false,
    tenantId: DEFAULT_TENANT_ID,
    scopeKey: '0-myVar',
    processInstanceKey: '0001',
  };
  return {
    ...baseVariable,
    ...variable,
  };
};

function getQueryVariablesResponseMock(
  variables: Variable[],
  totalItems: number = variables.length,
): QueryVariablesByUserTaskResponseBody {
  return {
    items: variables,
    page: {
      totalItems,
      startCursor: 'startCursor',
      endCursor: 'endCursor',
      hasMoreTotalItems: false,
    },
  };
}

export {
  variables,
  dynamicFormVariables,
  truncatedVariables,
  fullVariable,
  getQueryVariablesResponseMock,
};
