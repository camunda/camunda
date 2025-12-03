/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useSearchParams} from 'react-router-dom';
import {type QueryProcessInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {parseProcessInstancesSearchFilter} from 'modules/utils/filter/v2/processInstancesSearch';

type UseProcessInstanceQueryFiltersOptions = {
  includeIds?: string[];
  excludeIds?: string[];
};

function useProcessInstanceQueryFilters(
  options: UseProcessInstanceQueryFiltersOptions = {},
): Pick<QueryProcessInstancesRequestBody, 'filter'> {
  const [searchParams] = useSearchParams();
  const baseFilter = parseProcessInstancesSearchFilter(searchParams);

  if (!baseFilter) {
    return {filter: undefined};
  }

  if (options.includeIds || options.excludeIds) {
    const filter = {...baseFilter};

    if (options.includeIds && options.includeIds.length > 0) {
      const existingIds =
        typeof baseFilter.processInstanceKey === 'object' &&
        baseFilter.processInstanceKey?.$in
          ? baseFilter.processInstanceKey.$in
          : [];
      const mergedIds = [...new Set([...existingIds, ...options.includeIds])];
      filter.processInstanceKey = {$in: mergedIds};
    } else if (options.excludeIds && options.excludeIds.length > 0) {
      filter.processInstanceKey = {$notIn: options.excludeIds};
    }

    return {filter};
  }

  return {filter: baseFilter};
}

export {useProcessInstanceQueryFilters};
export type {UseProcessInstanceQueryFiltersOptions};
