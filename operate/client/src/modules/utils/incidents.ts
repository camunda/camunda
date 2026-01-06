/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  IncidentErrorType,
  QueryIncidentsRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import type {EnhancedIncident} from 'modules/hooks/incidents';

const ERROR_TYPE_NAMES: Record<IncidentErrorType, string> = {
  UNSPECIFIED: 'Unspecified',
  UNKNOWN: 'Unknown error',
  IO_MAPPING_ERROR: 'IO mapping error.',
  JOB_NO_RETRIES: 'Job: No retries left.',
  EXECUTION_LISTENER_NO_RETRIES: 'Execution listener error (no retries left).',
  TASK_LISTENER_NO_RETRIES: 'Task listener error (no retries left).',
  CONDITION_ERROR: 'Condition error.',
  EXTRACT_VALUE_ERROR: 'Extract value error.',
  CALLED_ELEMENT_ERROR: 'Called element error.',
  UNHANDLED_ERROR_EVENT: 'Unhandled error event.',
  MESSAGE_SIZE_EXCEEDED: 'Message size exceeded.',
  CALLED_DECISION_ERROR: 'Called decision error.',
  DECISION_EVALUATION_ERROR: 'Decision evaluation error.',
  FORM_NOT_FOUND: 'Form not found.',
  RESOURCE_NOT_FOUND: 'Resource not found.',
};

const availableErrorTypes = Object.keys(
  ERROR_TYPE_NAMES,
) as (keyof typeof ERROR_TYPE_NAMES)[];

const getIncidentErrorName = (errorType: IncidentErrorType): string => {
  return ERROR_TYPE_NAMES[errorType];
};

const isSingleIncidentSelected = (
  incidents: EnhancedIncident[],
  elementInstanceKey: string,
) => {
  const selectedInstances = incidents.filter((incident) => incident.isSelected);

  return (
    selectedInstances.length === 1 &&
    selectedInstances[0]?.elementInstanceKey === elementInstanceKey
  );
};

const getIncidentsSearchFilter = (
  errorTypes: IncidentErrorType[],
  elementId?: string,
): QueryIncidentsRequestBody['filter'] => {
  return {
    errorType: errorTypes.length > 0 ? {$in: errorTypes} : undefined,
    elementId,
  };
};

export {
  availableErrorTypes,
  getIncidentErrorName,
  isSingleIncidentSelected,
  getIncidentsSearchFilter,
};
