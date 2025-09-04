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
      hasIncident: baseFilter.incidents,
    },
  };

  const keyCriterion = buildProcessInstanceKeyCriterion(includeIds, excludeIds);
  if (keyCriterion) {
    requestBody.filter.processInstanceKey = keyCriterion;
  }

  if (processDefinitionKey) {
    requestBody.filter.processDefinitionKey = processDefinitionKey;
  }

  return requestBody;
};

export {buildMutationRequestBody};
