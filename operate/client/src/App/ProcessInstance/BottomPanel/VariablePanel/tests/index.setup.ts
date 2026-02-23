/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryVariablesResponseBody} from '@camunda/camunda-api-zod-schemas/8.8';

const mockVariables: QueryVariablesResponseBody = {
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

export {mockVariables};
