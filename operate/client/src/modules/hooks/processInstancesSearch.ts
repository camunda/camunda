/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  parseProcessInstancesSearchFilter,
  parseProcessInstancesSearchSort,
} from 'modules/utils/filter/v2/processInstancesSearch';
import {useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';

function useProcessInstancesSearchFilter() {
  const [searchParams] = useSearchParams();

  return useMemo(
    () => parseProcessInstancesSearchFilter(searchParams),
    [searchParams],
  );
}

function useProcessInstancesSearchSort() {
  const [searchParams] = useSearchParams();

  return useMemo(
    () => parseProcessInstancesSearchSort(searchParams),
    [searchParams],
  );
}

export {useProcessInstancesSearchFilter, useProcessInstancesSearchSort};
