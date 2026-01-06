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
import {parseProcessInstancesSearchFilter} from 'modules/utils/filter/v2/processInstancesSearch';
import {buildProcessInstanceKeyCriterion} from 'modules/mutations/processes/buildProcessInstanceKeyCriterion';
import {getValidVariableValues} from 'modules/utils/filter/getValidVariableValues';
import type {Variable} from 'modules/stores/variableFilter';

type BuildMutationRequestBodyParams = {
  searchParams: URLSearchParams;
  includeIds?: string[];
  excludeIds?: string[];
  variableFilter?: Variable;
  processDefinitionKey?: string;
};

const buildMutationRequestBody = ({
  searchParams,
  includeIds = [],
  excludeIds = [],
  variableFilter,
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

  if (variableFilter?.name && variableFilter?.values) {
    const parsed = (getValidVariableValues(variableFilter.values) ?? []).map(
      (v) => JSON.stringify(v),
    );
    if (parsed.length > 0) {
      filter = {
        ...filter,
        variables: [
          {
            name: variableFilter.name,
            value: parsed.length === 1 ? parsed[0]! : {$in: parsed},
          },
        ],
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
