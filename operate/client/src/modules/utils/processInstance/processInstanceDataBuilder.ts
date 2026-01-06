/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import type {ProcessInstanceEntity} from 'modules/types/operate';

// Adapts v2 API ProcessInstance to v1 store ProcessInstanceEntiy format

const buildV2ProcessInstanceData = (
  v2Instance: ProcessInstance,
): ProcessInstanceEntity => {
  return {
    id: v2Instance.processInstanceKey,
    processId: v2Instance.processDefinitionKey,
    processName: v2Instance.processDefinitionName,
    processVersion: v2Instance.processDefinitionVersion,
    startDate: v2Instance.startDate,
    endDate: v2Instance.endDate ?? null,
    state: v2Instance.hasIncident ? 'INCIDENT' : v2Instance.state,
    tenantId: v2Instance.tenantId,
    parentInstanceId: v2Instance.parentProcessInstanceKey ?? null,
    bpmnProcessId: v2Instance.processDefinitionId ?? null,
    // Fields not in v2 API - using defaults
    hasActiveOperation: false,
    operations: [],
    sortValues: [],
    rootInstanceId: null,
    callHierarchy: [],
  };
};

export {buildV2ProcessInstanceData};
