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
import type {RequestFilters} from 'modules/utils/filter';
import {
  buildProcessInstanceFilter,
  type BuildProcessInstanceFilterOptions,
} from 'modules/utils/filter/v2/processInstanceFilterBuilder';

type BuildMutationRequestBodyParams = {
  baseFilter: RequestFilters;
  includeIds: string[];
  excludeIds: string[];
};

const buildMutationRequestBody = ({
  baseFilter,
  includeIds = [],
  excludeIds = [],
}: BuildMutationRequestBodyParams) => {
  const builderOptions: BuildProcessInstanceFilterOptions = {
    includeIds,
    excludeIds,
  };

  const filter = buildProcessInstanceFilter(baseFilter, builderOptions);

  const requestBody:
    | CreateIncidentResolutionBatchOperationRequestBody
    | CreateCancellationBatchOperationRequestBody = {
    filter,
  };

  return requestBody;
};

export {buildMutationRequestBody};
