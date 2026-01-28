/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessDefinitionInstanceStatistics} from '@camunda/camunda-api-zod-schemas/8.8';

function createProcessDefinitionInstancesStatistics(
  options?: Partial<ProcessDefinitionInstanceStatistics>,
): ProcessDefinitionInstanceStatistics {
  return {
    processDefinitionId: 'orderProcess',
    latestProcessDefinitionName: 'Order process',
    hasMultipleVersions: true,
    activeInstancesWithIncidentCount: 0,
    activeInstancesWithoutIncidentCount: 0,
    tenantId: '<default>',
    ...options,
  };
}

export {createProcessDefinitionInstancesStatistics};
