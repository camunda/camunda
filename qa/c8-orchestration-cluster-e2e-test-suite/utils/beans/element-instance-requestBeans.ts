/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

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
