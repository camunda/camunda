/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useHistory} from 'react-router-dom';

import {
  updateFiltersSearchString,
  getFilters,
  FiltersType,
} from 'modules/utils/filter';

const useFilters = () => {
  const history = useHistory();

  const setFiltersToURL = (filters: FiltersType) => {
    history.push({
      ...history.location,
      search: updateFiltersSearchString(history.location.search, filters),
    });
  };

  const getFiltersFromUrl = () => getFilters(history.location.search);

  const areProcessInstanceStatesApplied = () => {
    const filters = getFilters(history.location.search);
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
