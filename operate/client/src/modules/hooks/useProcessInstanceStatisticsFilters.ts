/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useSearchParams} from 'react-router-dom';
import {type GetProcessDefinitionStatisticsRequestBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {parseProcessInstancesSearchFilter} from 'modules/utils/filter/v2/processInstancesSearch';

function useProcessInstanceStatisticsFilters(): GetProcessDefinitionStatisticsRequestBody {
  const [searchParams] = useSearchParams();
  const fullFilter = parseProcessInstancesSearchFilter(searchParams);

  if (!fullFilter) {
    return {filter: undefined};
  }

  // Exclude processDefinitionVersion - not supported by statistics endpoint
  const {processDefinitionVersion: _, ...statisticsFilter} = fullFilter;

  return {
    filter: statisticsFilter,
  };
}

export {useProcessInstanceStatisticsFilters};
