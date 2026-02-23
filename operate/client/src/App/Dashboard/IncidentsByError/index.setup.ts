/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  IncidentProcessInstanceStatisticsByDefinition,
  IncidentProcessInstanceStatisticsByError,
} from '@camunda/camunda-api-zod-schemas/8.9';
import {searchResult} from 'modules/testUtils';

const bigErrorMessage =
  'Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore et dolore magna aliqua. Tempor nec feugiat nisl pretium fusce id. Pulvinar sapien et ligula ullamcorper malesuada. Iaculis nunc sed augue lacus viverra vitae congue eu. Aliquet lectus proin nibh nisl condimentum id. Tempus iaculis urna id volutpat.';

const mockIncidentsByError =
  searchResult<IncidentProcessInstanceStatisticsByError>([
    {
      errorHashCode: 234254,
      errorMessage: "JSON path '$.paid' has no result.",
      activeInstancesWithErrorCount: 78,
    },
  ]);

const mockIncidentStatisticsByErrorWithBigMessage =
  searchResult<IncidentProcessInstanceStatisticsByError>([
    {
      errorHashCode: 234254,
      errorMessage: bigErrorMessage,
      activeInstancesWithErrorCount: 36,
    },
  ]);

const mockIncidentStatisticsByDefinition =
  searchResult<IncidentProcessInstanceStatisticsByDefinition>([
    {
      processDefinitionId: 'call-level-2-process',
      processDefinitionKey: 1,
      processDefinitionVersion: 1,
      processDefinitionName: 'Call Level 2 Process – Version 1',
      tenantId: '<default>',
      activeInstancesWithErrorCount: 52,
    },
    {
      processDefinitionId: 'process-elements-incidents',
      processDefinitionKey: 2,
      processDefinitionVersion: 1,
      processDefinitionName: 'Process with elements incidents – Version 1',
      tenantId: 'tenant-a',
      activeInstancesWithErrorCount: 26,
    },
  ]);

export {
  mockIncidentsByError,
  mockIncidentStatisticsByDefinition,
  bigErrorMessage,
  mockIncidentStatisticsByErrorWithBigMessage,
};
