/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export const filterCases = (resourceId: string) => [
  {
    filterKey: 'processDefinitionId',
    filterValue: resourceId,
    expectedTotal: 2,
  },
  {
    filterKey: 'elementName',
    filterValue: 'Decision If Human Needed',
    expectedTotal: 1,
  },
  {
    filterKey: 'type',
    filterValue: 'EXCLUSIVE_GATEWAY',
    expectedTotal: 1,
  },
  {
    filterKey: 'processDefinitionKey',
    filterValue: '',
    expectedTotal: 2,
  },
  {
    filterKey: 'processInstanceKey',
    filterValue: '',
    expectedTotal: 2,
  },
];

export const EXPECTED_ELEMENT_INSTANCE_GET_SUCCESS = (
  processInstanceKey: string,
  elementInstanceKey: string,
) => ({
  processDefinitionId: 'element_instance_get_update_tests',
  elementId: 'prepare_work_task',
  elementName: 'prepare work',
  type: 'USER_TASK',
  state: 'ACTIVE',
  hasIncident: false,
  tenantId: '<default>',
  elementInstanceKey: elementInstanceKey,
  processInstanceKey: processInstanceKey,
});
