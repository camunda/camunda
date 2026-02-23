/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  GetProcessDefinitionInstanceStatisticsResponseBody,
  GetProcessDefinitionInstanceVersionStatisticsResponseBody,
} from '@camunda/camunda-api-zod-schemas/8.8';

const mockWithSingleVersion: GetProcessDefinitionInstanceStatisticsResponseBody =
  {
    items: [
      {
        processDefinitionId: 'loanProcess',
        latestProcessDefinitionName: 'loanProcess',
        hasMultipleVersions: false,
        activeInstancesWithIncidentCount: 16,
        activeInstancesWithoutIncidentCount: 122,
        tenantId: '<default>',
      },
    ],
    page: {totalItems: 1},
  };

const mockWithMultipleVersions: GetProcessDefinitionInstanceStatisticsResponseBody =
  {
    items: [
      {
        processDefinitionId: 'orderProcess',
        latestProcessDefinitionName: 'Order process',
        hasMultipleVersions: true,
        activeInstancesWithIncidentCount: 65,
        activeInstancesWithoutIncidentCount: 136,
        tenantId: '<default>',
      },
    ],
    page: {totalItems: 1},
  };

const mockOrderProcessVersions: GetProcessDefinitionInstanceVersionStatisticsResponseBody =
  {
    items: [
      {
        processDefinitionId: 'mockProcess',
        processDefinitionKey: 'mockProcess-1',
        processDefinitionName: 'First Version',
        processDefinitionVersion: 1,
        activeInstancesWithIncidentCount: 37,
        activeInstancesWithoutIncidentCount: 5,
        tenantId: '<default>',
      },
      {
        processDefinitionId: 'mockProcess',
        processDefinitionKey: 'mockProcess-2',
        processDefinitionName: 'Second Version',
        processDefinitionVersion: 2,
        activeInstancesWithIncidentCount: 37,
        activeInstancesWithoutIncidentCount: 5,
        tenantId: '<default>',
      },
    ],
    page: {totalItems: 2},
  };

export {
  mockWithSingleVersion,
  mockWithMultipleVersions,
  mockOrderProcessVersions,
};
