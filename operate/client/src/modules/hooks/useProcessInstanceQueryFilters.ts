/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useFilters} from 'modules/hooks/useFilters';
import {type QueryProcessInstancesRequestBody} from '@camunda/camunda-api-zod-schemas/8.8';
import {
  buildProcessInstanceFilter,
  type BuildProcessInstanceFilterOptions,
} from 'modules/utils/filter/v2/processInstanceFilterBuilder';

function useProcessInstanceQueryFilters(
  options: Omit<
    BuildProcessInstanceFilterOptions,
    'includeIds' | 'excludeIds'
  > = {},
): Pick<QueryProcessInstancesRequestBody, 'filter'> {
  const {getFilters} = useFilters();
  const filters = getFilters();

  // Query endpoints support all fields, including processDefinitionVersionTag
  const filter = buildProcessInstanceFilter(filters, options);

  return {filter};
}

export {useProcessInstanceQueryFilters};
