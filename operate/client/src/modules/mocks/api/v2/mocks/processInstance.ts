/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.10';

const mockProcessInstance = {
  processInstanceKey: '4294980768',
  state: 'ACTIVE',
  startDate: '2018-06-21',
  processDefinitionKey: '2',
  processDefinitionVersion: 1,
  processDefinitionId: 'someKey',
  tenantId: '<default>',
  processDefinitionName: 'someProcessName',
  hasIncident: true,
  processDefinitionVersionTag: null,
  endDate: null,
  parentProcessInstanceKey: null,
  parentElementInstanceKey: null,
  rootProcessInstanceKey: null,
  tags: [],
  businessId: null,
} satisfies ProcessInstance;

export {mockProcessInstance};
