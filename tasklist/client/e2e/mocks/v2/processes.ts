/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessDefinition} from '@vzeta/camunda-api-zod-schemas/8.8';

const mockProcesses = [
  {
    resourceName: 'multipleVersions.bpmn',
    version: 1,
    processDefinitionId: 'multipleVersions',
    tenantId: '<default>',
    processDefinitionKey: '0',
    hasStartForm: false,
  },
  {
    resourceName: 'orderProcess.bpmn',
    version: 1,
    processDefinitionId: 'orderProcess',
    tenantId: '<default>',
    processDefinitionKey: '1',
    hasStartForm: false,
  },
] satisfies Array<ProcessDefinition>;

export {mockProcesses};
