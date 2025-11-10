/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useFilters} from 'modules/hooks/useFilters';
import {
  getProcessDefinitionStatisticsRequestBodySchema,
  type GetProcessDefinitionStatisticsRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import type {ProcessInstanceFilters} from 'modules/utils/filter/shared';
import {
  buildProcessInstanceFilter,
  type BuildProcessInstanceFilterOptions,
} from 'modules/utils/filter/v2/processInstanceFilterBuilder';

function mapFiltersToRequest(
  filters: ProcessInstanceFilters,
  options: Omit<
    BuildProcessInstanceFilterOptions,
    'includeIds' | 'excludeIds'
  > = {},
): GetProcessDefinitionStatisticsRequestBody {
  const builderOptions: BuildProcessInstanceFilterOptions = {
    ...options,
  };

  const filter = buildProcessInstanceFilter(filters, builderOptions);
  const result = getProcessDefinitionStatisticsRequestBodySchema.parse({
    filter,
  });

  return result;
}

function useProcessInstanceFilters(
  options: Omit<
    BuildProcessInstanceFilterOptions,
    'includeIds' | 'excludeIds'
  > = {},
): GetProcessDefinitionStatisticsRequestBody {
  const {getFilters} = useFilters();
  const filters = getFilters();

  return mapFiltersToRequest(filters, options);
}

export {useProcessInstanceFilters};
