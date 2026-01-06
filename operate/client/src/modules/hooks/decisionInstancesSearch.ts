/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  parseDecisionInstancesSearchFilter,
  parseDecisionInstancesSearchSort,
} from 'modules/utils/filter/decisionsFilter';
import {useMemo} from 'react';
import {useSearchParams} from 'react-router-dom';

function useDecisionInstancesSearchFilter() {
  const [searchParams] = useSearchParams();

  return useMemo(
    () => parseDecisionInstancesSearchFilter(searchParams),
    [searchParams],
  );
}

function useDecisionInstancesSearchSort() {
  const [searchParams] = useSearchParams();

  return useMemo(
    () => parseDecisionInstancesSearchSort(searchParams),
    [searchParams],
  );
}

export {useDecisionInstancesSearchFilter, useDecisionInstancesSearchSort};
