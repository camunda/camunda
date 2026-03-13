/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {RequestFilters} from 'modules/utils/filter';
import type {
  CreateCancellationBatchOperationRequestBody,
  CreateIncidentResolutionBatchOperationRequestBody,
  ProcessInstance,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {buildProcessInstanceKeyCriterion} from 'modules/mutations/processes/buildProcessInstanceKeyCriterion';
import {formatToISO} from 'modules/utils/date/formatDate';

type BuildMutationRequestBodyParams = {
  baseFilter: RequestFilters;
  includeIds: string[];
  excludeIds: string[];
  processDefinitionKey?: string;
};

const buildMutationRequestBody = ({
  baseFilter,
  includeIds,
  excludeIds,
  processDefinitionKey,
}: BuildMutationRequestBodyParams) => {
  const requestBody:
    | CreateIncidentResolutionBatchOperationRequestBody
    | CreateCancellationBatchOperationRequestBody = {
    filter: {
      elementId: baseFilter.activityId,
      errorMessage: baseFilter.errorMessage,
      tenantId: baseFilter.tenantId,
      batchOperationId: baseFilter.batchOperationId,
      parentProcessInstanceKey: baseFilter.parentInstanceId,
      hasRetriesLeft: baseFilter.retriesLeft,
      incidentErrorHashCode: baseFilter.incidentErrorHashCode,
    },
  };

  const states: ProcessInstance['state'][] = [];
  if (baseFilter.active) {
    states.push('ACTIVE');
  }
  if (baseFilter.completed) {
    states.push('COMPLETED');
  }
  if (baseFilter.canceled) {
    states.push('TERMINATED');
  }

  if (baseFilter.incidents) {
    if (states.length > 0) {
      requestBody.filter.$or = [{state: {$in: states}}, {hasIncident: true}];
    } else {
      requestBody.filter.hasIncident = true;
    }
  } else if (states.length > 0) {
    requestBody.filter.state =
      states.length === 1 ? {$eq: states[0]} : {$in: states};
    requestBody.filter.hasIncident = false;
  }

  if (baseFilter.activityId) {
    requestBody.filter.elementInstanceState = {$eq: 'ACTIVE'};
  }

  if (
    Array.isArray(baseFilter.processIds) &&
    baseFilter.processIds.length > 0 &&
    !processDefinitionKey
  ) {
    requestBody.filter.processDefinitionKey = {
      $in: baseFilter.processIds,
    };
  }

  if (processDefinitionKey) {
    requestBody.filter.processDefinitionKey = processDefinitionKey;
  }

  if (baseFilter.startDateAfter || baseFilter.startDateBefore) {
    const startDate: {
      $gt?: string;
      $lt?: string;
    } = {};

    if (baseFilter.startDateAfter) {
      startDate.$gt = formatToISO(baseFilter.startDateAfter);
    }
    if (baseFilter.startDateBefore) {
      startDate.$lt = formatToISO(baseFilter.startDateBefore);
    }

    requestBody.filter.startDate = startDate;
  }

  if (baseFilter.endDateAfter || baseFilter.endDateBefore) {
    const endDate: {
      $gt?: string;
      $lt?: string;
    } = {};

    if (baseFilter.endDateAfter) {
      endDate.$gt = formatToISO(baseFilter.endDateAfter);
    }
    if (baseFilter.endDateBefore) {
      endDate.$lt = formatToISO(baseFilter.endDateBefore);
    }

    requestBody.filter.endDate = endDate;
  }

  if (
    baseFilter.variable?.name &&
    Array.isArray(baseFilter.variable.values) &&
    baseFilter.variable.values.length > 0
  ) {
    const values = baseFilter.variable.values;
    requestBody.filter.variables = [
      {
        name: baseFilter.variable.name,
        value: values.length === 1 ? values[0] : {$in: values},
      },
    ];
  }

  const keyCriterion = buildProcessInstanceKeyCriterion(includeIds, excludeIds);
  if (keyCriterion) {
    requestBody.filter.processInstanceKey = keyCriterion;
  }

  return requestBody;
};

export {buildMutationRequestBody};
