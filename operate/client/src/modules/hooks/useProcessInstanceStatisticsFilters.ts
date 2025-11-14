/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useFilters} from 'modules/hooks/useFilters';
import {type GetProcessDefinitionStatisticsRequestBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {
  buildProcessInstanceFilter,
  type BuildProcessInstanceFilterOptions,
} from 'modules/utils/filter/v2/processInstanceFilterBuilder';

/**
 * Hook for building filters for the process instance statistics endpoint.
 *
 * This hook builds filters compatible with the statistics API endpoint which uses
 * the processDefinitionStatisticsFilterFieldsSchema. This schema does NOT support
 * the processDefinitionVersionTag field, so it is explicitly excluded.
 *
 * @param options - Optional build options (businessObjects, processDefinitionKeys, etc.)
 * @returns Request body for GetProcessDefinitionStatistics endpoint
 *
 * @example
 * const filters = useProcessInstanceStatisticsFilters({
 *   processDefinitionKeys: ['process1', 'process2']
 * });
 */
function useProcessInstanceStatisticsFilters(
  options: Omit<
    BuildProcessInstanceFilterOptions,
    'includeIds' | 'excludeIds'
  > = {},
): GetProcessDefinitionStatisticsRequestBody {
  const {getFilters} = useFilters();
  const filters = getFilters();

  // Build the full filter from URL params
  const fullFilter = buildProcessInstanceFilter(filters, options);

  // Exclude processDefinitionVersionTag - not supported by statistics endpoint
  const {processDefinitionVersionTag, ...statisticsFilter} = fullFilter;

  return {
    filter: statisticsFilter,
  };
}

export {useProcessInstanceStatisticsFilters};
