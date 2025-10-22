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
} from '@camunda/camunda-api-zod-schemas/8.8';
import {buildProcessInstanceKeyCriterion} from 'modules/mutations/processes/buildProcessInstanceKeyCriterion';
import {formatToISO} from 'modules/utils/date/formatDate';

type BuildMutationRequestBodyParams = {
  baseFilter: RequestFilters;
  includeIds: string[];
  excludeIds: string[];
};

const buildMutationRequestBody = ({
  baseFilter,
  includeIds,
  excludeIds,
}: BuildMutationRequestBodyParams) => {
  const requestBody:
    | CreateIncidentResolutionBatchOperationRequestBody
    | CreateCancellationBatchOperationRequestBody = {
    filter: {
      elementId: baseFilter.activityId,
      errorMessage: baseFilter.errorMessage,
      tenantId: baseFilter.tenantId,
      batchOperationKey: baseFilter.batchOperationId,
      parentProcessInstanceKey: baseFilter.parentInstanceId,
      hasRetriesLeft: baseFilter.retriesLeft,
      incidentErrorHashCode: baseFilter.incidentErrorHashCode,
    },
  };

  if (baseFilter.incidents && baseFilter.active) {
    requestBody.filter.$or = [{hasIncident: true}, {state: {$eq: 'ACTIVE'}}];
  } else if (baseFilter.incidents) {
    requestBody.filter.hasIncident = true;
  } else if (baseFilter.active) {
    requestBody.filter.state = {$eq: 'ACTIVE'};
  }

  if (baseFilter.completed && baseFilter.canceled) {
    requestBody.filter.state = {$in: ['COMPLETED', 'TERMINATED']};
  } else if (baseFilter.completed) {
    requestBody.filter.state = {$eq: 'COMPLETED'};
  } else if (baseFilter.canceled) {
    requestBody.filter.state = {$eq: 'TERMINATED'};
  }

  if (baseFilter.processIds && baseFilter.processIds.length > 0) {
    requestBody.filter.processDefinitionKey = {
      $in: baseFilter.processIds,
    };
  }

  if (baseFilter.startDateAfter || baseFilter.startDateBefore) {
    requestBody.filter.startDate = {
      ...(baseFilter.startDateAfter && {
        $gt: formatToISO(baseFilter.startDateAfter),
      }),
      ...(baseFilter.startDateBefore && {
        $lt: formatToISO(baseFilter.startDateBefore),
      }),
    };
  }

  if (baseFilter.endDateAfter || baseFilter.endDateBefore) {
    requestBody.filter.endDate = {
      ...(baseFilter.endDateAfter && {
        $gt: formatToISO(baseFilter.endDateAfter),
      }),
      ...(baseFilter.endDateBefore && {
        $lt: formatToISO(baseFilter.endDateBefore),
      }),
    };
  }

  if (baseFilter.variable?.name && baseFilter.variable.values) {
    requestBody.filter.variables = baseFilter.variable.values.map((value) => {
      return {
        name: baseFilter.variable!.name,
        value,
      };
    });
  }

  const keyCriterion = buildProcessInstanceKeyCriterion(includeIds, excludeIds);
  if (keyCriterion) {
    requestBody.filter.processInstanceKey = keyCriterion;
  }

  return requestBody;
};

export {buildMutationRequestBody};
