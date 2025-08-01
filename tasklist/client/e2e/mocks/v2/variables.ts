/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Variable} from '@vzeta/camunda-api-zod-schemas/8.8';

const variables = [
  {
    name: 'small',
    value: '"Hello World"',
    isTruncated: false,
    tenantId: 'default',
    processInstanceKey: '123',
    variableKey: '2251799813686711-small',
    scopeKey: '123',
  },
] satisfies Variable[];

export {variables};
