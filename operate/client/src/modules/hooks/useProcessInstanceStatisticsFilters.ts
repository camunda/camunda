/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useSearchParams} from 'react-router-dom';
import {
  type GetProcessDefinitionStatisticsRequestBody,
  type QueryProcessInstancesRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.8';
import {parseProcessInstancesSearchFilter} from 'modules/utils/filter/v2/processInstancesSearch';

type ProcessInstancesSearchFilter = NonNullable<
  QueryProcessInstancesRequestBody['filter']
>;
type StatisticsFilter = NonNullable<
  GetProcessDefinitionStatisticsRequestBody['filter']
>;

const getValidStatisticsFilters = (
  fullFilter: ProcessInstancesSearchFilter,
): StatisticsFilter => {
  const {
    processDefinitionId,
    processDefinitionName,
    processDefinitionKey,
    processDefinitionVersion,
    processDefinitionVersionTag,
    ...statisticsFilter
  } = fullFilter;

  return statisticsFilter;
};

const useProcessInstanceStatisticsFilters =
  (): GetProcessDefinitionStatisticsRequestBody => {
    const [searchParams] = useSearchParams();
    const fullFilter = parseProcessInstancesSearchFilter(searchParams);

    if (!fullFilter) {
      return {filter: undefined};
    }

    return {
      filter: getValidStatisticsFilters(fullFilter),
    };
  };

export {useProcessInstanceStatisticsFilters, getValidStatisticsFilters};
