/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryVariablesResponseBody} from '@vzeta/camunda-api-zod-schemas';
import type {VariableEntity} from 'modules/types/operate';

const mockVariables: VariableEntity[] = [
  {
    id: '2251799813686037-clientNo',
    name: 'clientNo',
    value: '"CNT-1211132-0223222"',
    hasActiveOperation: false,
    isFirst: true,
    isPreview: false,
    sortValues: ['clientNo'],
  },
  {
    id: '2251799813686037-mwst',
    name: 'mwst',
    value: '124.26',
    hasActiveOperation: false,
    isFirst: false,
    isPreview: false,
    sortValues: ['mwst'],
  },
  {
    id: '2251799813686037-mwst',
    name: 'active-operation-variable',
    value: '1',
    hasActiveOperation: true,
    isFirst: false,
    isPreview: false,
    sortValues: ['active-operation-variable'],
  },
];

const mockVariablesV2: QueryVariablesResponseBody = {
  items: [
    {
      value: '"CNT-1211132-0223222"',
      isTruncated: false,
      name: 'clientNo',
      tenantId: '<default>',
      variableKey: '2251799813686037-clientNo',
      scopeKey: '2251799813696123',
      processInstanceKey: '2251799813696123',
    },
    {
      value: '124.26',
      isTruncated: false,
      name: 'mwst',
      tenantId: '<default>',
      variableKey: '2251799813686037-mwst',
      scopeKey: '2251799813696123',
      processInstanceKey: '2251799813696123',
    },
    {
      value: '1',
      isTruncated: false,
      name: 'active-operation-variable',
      tenantId: '<default>',
      variableKey: '2251799813686037-mwst',
      scopeKey: '2251799813696123',
      processInstanceKey: '2251799813696123',
    },
  ],
  page: {
    totalItems: 3,
  },
};

export {mockVariables, mockVariablesV2};
