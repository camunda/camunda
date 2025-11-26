/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import type {ProcessInstanceEntity} from 'modules/types/operate';

/** Maps a {@link ProcessInstance} from the orchestration-cluster API to a V1 {@link ProcessInstanceEntity}. */
function mapProcessInstanceToV1Entity(
  instance: ProcessInstance,
): ProcessInstanceEntity {
  return {
    id: instance.processInstanceKey,
    processId: instance.processDefinitionKey,
    processName: instance.processDefinitionName,
    processVersion: instance.processDefinitionVersion,
    startDate: instance.startDate,
    endDate: instance.endDate ?? null,
    state: instance.hasIncident ? 'INCIDENT' : instance.state,
    tenantId: instance.tenantId,
    parentInstanceId: instance.parentProcessInstanceKey ?? null,
    bpmnProcessId: instance.processDefinitionId ?? null,
    // Fields not in v2 API - using defaults
    hasActiveOperation: false,
    operations: [],
    sortValues: [],
    rootInstanceId: null,
    callHierarchy: [],
    permissions: undefined,
  };
}

export {mapProcessInstanceToV1Entity};
