/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {useSearchParams} from 'react-router';
import {
  type GetProcessDefinitionStatisticsRequestBody,
  type QueryProcessInstancesRequestBody,
} from '@camunda/camunda-api-zod-schemas/8.9';
import {parseProcessInstancesSearchFilter} from 'modules/utils/filter/v2/processInstancesSearch';
import {getValidVariableValues} from 'modules/utils/filter/getValidVariableValues';
import type {Variable} from 'modules/stores/variableFilter';

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
  variable?: Variable,
): GetProcessDefinitionStatisticsRequestBody => {
  const [searchParams] = useSearchParams();

  return useMemo(() => {
    const fullFilter = parseProcessInstancesSearchFilter(searchParams);

    if (!fullFilter) {
      return {filter: undefined};
    }

    if (variable?.name && variable?.values) {
      const parsed = (getValidVariableValues(variable.values) ?? []).map((v) =>
        JSON.stringify(v),
      );
      if (parsed.length > 0) {
        fullFilter.variables = [
          {
            name: variable?.name,
            value: parsed.length === 1 ? parsed[0]! : {$in: parsed},
          },
        ];
      }
    }

    return {
      filter: getValidStatisticsFilters(fullFilter),
    };
  }, [searchParams, variable]);
};

export {useProcessInstanceStatisticsFilters, getValidStatisticsFilters};
