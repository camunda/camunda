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
} from '@camunda/camunda-api-zod-schemas/8.10';
import {parseProcessInstancesSearchFilter} from 'modules/utils/filter/processInstancesSearch';
import {buildInstanceKeyCriterion} from 'modules/utils/instances/buildInstanceKeyCriterion';
import type {VariableCondition} from 'modules/stores/variableFilter';
import {buildVariableEntry} from 'modules/hooks/processInstancesSearch';

type BuildMutationRequestBodyParams = {
  searchParams: URLSearchParams;
  includeIds?: string[];
  excludeIds?: string[];
  conditions?: VariableCondition[];
  processDefinitionKey?: string;
};

const buildMutationRequestBody = ({
  searchParams,
  includeIds = [],
  excludeIds = [],
  conditions,
  processDefinitionKey,
}: BuildMutationRequestBodyParams) => {
  const baseFilter = parseProcessInstancesSearchFilter(searchParams);

  const keyCriterion = buildInstanceKeyCriterion(includeIds, excludeIds);

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

  if (conditions && conditions.length > 0) {
    filter = {
      ...filter,
      variables: conditions.map(buildVariableEntry),
    };
  }

  const requestBody:
    | CreateIncidentResolutionBatchOperationRequestBody
    | CreateCancellationBatchOperationRequestBody = {
    filter,
  };

  return requestBody;
};

export {buildMutationRequestBody};
