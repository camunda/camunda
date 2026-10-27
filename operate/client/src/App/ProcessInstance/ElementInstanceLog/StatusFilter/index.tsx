/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

/* eslint-disable react-refresh/only-export-components -- this module intentionally co-locates the elementStatus URL-param key and reader with the component that owns the param */

import {useNavigate, useSearchParams} from 'react-router-dom';
import {
  FilterSwitcher,
  FilterSwitcherButton,
} from 'modules/components/FilterSwitcher/styled';

const STATUS_PARAM_KEY = 'elementStatus';

type StatusFilterValue = 'active' | 'incidents';

const getStatusFilter = (
  searchParams: URLSearchParams,
): StatusFilterValue | null => {
  const value = searchParams.get(STATUS_PARAM_KEY);
  return value === 'active' || value === 'incidents' ? value : null;
};

const StatusFilter: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const currentFilter = getStatusFilter(searchParams);

  const setStatusFilter = (value: StatusFilterValue | null) => {
    const next = new URLSearchParams(searchParams);
    if (value === null) {
      next.delete(STATUS_PARAM_KEY);
    } else {
      next.set(STATUS_PARAM_KEY, value);
    }
    navigate({search: next.toString()}, {replace: true});
  };

  return (
    <FilterSwitcher role="group" aria-label="Instance status filter">
      <FilterSwitcherButton
        type="button"
        aria-pressed={currentFilter === null}
        onClick={() => setStatusFilter(null)}
        data-testid="filter-instance-history-all"
      >
        All
      </FilterSwitcherButton>
      <FilterSwitcherButton
        type="button"
        aria-pressed={currentFilter === 'active'}
        onClick={() => setStatusFilter('active')}
        data-testid="filter-instance-history-active"
      >
        Active
      </FilterSwitcherButton>
      <FilterSwitcherButton
        type="button"
        aria-pressed={currentFilter === 'incidents'}
        onClick={() => setStatusFilter('incidents')}
        data-testid="filter-instance-history-incidents"
      >
        Incidents
      </FilterSwitcherButton>
    </FilterSwitcher>
  );
};

export {StatusFilter, STATUS_PARAM_KEY, getStatusFilter};
export type {StatusFilterValue};
