/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  CreateCancellationBatchOperationRequestBody,
  CreateIncidentResolutionBatchOperationRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {
  parseProcessInstancesSearchFilter,
  convertVariableConditionsToApiFormat,
} from 'modules/utils/filter/v2/processInstancesSearch';
import {buildProcessInstanceKeyCriterion} from 'modules/mutations/processes/buildProcessInstanceKeyCriterion';
import type {VariableCondition} from 'modules/stores/variableFilter';

type BuildMutationRequestBodyParams = {
  searchParams: URLSearchParams;
  includeIds?: string[];
  excludeIds?: string[];
  variableConditions?: VariableCondition[];
  processDefinitionKey?: string;
};

const buildMutationRequestBody = ({
  searchParams,
  includeIds = [],
  excludeIds = [],
  variableConditions,
  processDefinitionKey,
}: BuildMutationRequestBodyParams) => {
  const baseFilter = parseProcessInstancesSearchFilter(searchParams);

  const keyCriterion = buildProcessInstanceKeyCriterion(includeIds, excludeIds);

  let filter = baseFilter ?? {};

  if (keyCriterion) {
    filter = {...filter, processInstanceKey: keyCriterion};
  }

  if (processDefinitionKey !== undefined) {
    filter = {
      ...filter,
      processDefinitionKey: processDefinitionKey,
    };
  }

  if (variableConditions && variableConditions.length > 0) {
    const apiVariables =
      convertVariableConditionsToApiFormat(variableConditions);
    if (apiVariables.length > 0) {
      filter = {
        ...filter,
        variables: apiVariables,
      };
    }
  }

  const requestBody:
    | CreateIncidentResolutionBatchOperationRequestBody
    | CreateCancellationBatchOperationRequestBody = {
    filter,
  };

  return requestBody;
};

export {buildMutationRequestBody};
