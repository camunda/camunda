/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {useNavigate, useLocation} from 'react-router-dom';
import {
  updateProcessFiltersSearchString,
  getProcessInstanceFilters,
  ProcessInstanceFilters,
  getDecisionInstanceFilters,
} from 'modules/utils/filter';

const useFilters = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const setFiltersToURL = (filters: ProcessInstanceFilters) => {
    navigate({
      search: updateProcessFiltersSearchString(location.search, filters),
    });
  };

  const getFiltersFromUrl = () => getProcessInstanceFilters(location.search);

  const areProcessInstanceStatesApplied = () => {
    const filters = getProcessInstanceFilters(location.search);

    return (
      filters.active ||
      filters.incidents ||
      filters.completed ||
      filters.canceled
    );
  };

  const areDecisionInstanceStatesApplied = () => {
    const filters = getDecisionInstanceFilters(location.search);

    return filters.evaluated || filters.failed;
  };

  return {
    setFiltersToURL,
    getFiltersFromUrl,
    areProcessInstanceStatesApplied,
    areDecisionInstanceStatesApplied,
  };
};

export {useFilters};
