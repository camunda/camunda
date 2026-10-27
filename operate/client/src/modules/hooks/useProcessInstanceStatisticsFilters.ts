/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';
import {
  type GetProcessDefinitionStatisticsRequestBody,
  type QueryProcessInstancesRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {parseProcessInstancesSearchFilter} from 'modules/utils/filter/processInstancesSearch';
import type {VariableCondition} from 'modules/stores/variableFilter';
import {buildVariableEntry} from 'modules/hooks/processInstancesSearch';

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

const useProcessInstanceStatisticsFilters = (
  conditions?: VariableCondition[],
): GetProcessDefinitionStatisticsRequestBody => {
  const [searchParams] = useSearchParams();

  return useMemo(() => {
    const fullFilter = parseProcessInstancesSearchFilter({
      searchParams,
      includeSuspended: true,
    });

    if (!fullFilter) {
      return {filter: undefined};
    }

    if (conditions && conditions.length > 0) {
      const entries = conditions
        .map(buildVariableEntry)
        .filter((entry): entry is NonNullable<typeof entry> => entry !== null);
      if (entries.length > 0) {
        fullFilter.variables = entries;
      }
    }

    return {
      filter: getValidStatisticsFilters(fullFilter),
    };
  }, [searchParams, conditions]);
};

export {useProcessInstanceStatisticsFilters, getValidStatisticsFilters};
