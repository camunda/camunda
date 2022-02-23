/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useNavigate, useLocation} from 'react-router-dom';

import {
  updateFiltersSearchString,
  getFilters,
  FiltersType,
} from 'modules/utils/filter';

const useFilters = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const setFiltersToURL = (filters: FiltersType) => {
    navigate({
      ...location,
      search: updateFiltersSearchString(location.search, filters),
    });
  };

  const getFiltersFromUrl = () => getFilters(location.search);

  const areProcessInstanceStatesApplied = () => {
    const filters = getFilters(location.search);
    return (
      filters.active ||
      filters.incidents ||
      filters.completed ||
      filters.canceled
    );
  };

  return {setFiltersToURL, getFiltersFromUrl, areProcessInstanceStatesApplied};
};

export {useFilters};
