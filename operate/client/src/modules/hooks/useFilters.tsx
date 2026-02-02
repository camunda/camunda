/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useNavigate, useLocation} from 'react-router-dom';
import {updateProcessFiltersSearchString} from 'modules/utils/filter';
import {type ProcessInstanceFilters} from 'modules/utils/filter/shared';
import {getProcessInstanceFilters} from 'modules/utils/filter/getProcessInstanceFilters';

const useFilters = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const setFiltersToURL = (filters: ProcessInstanceFilters) => {
    navigate({
      search: updateProcessFiltersSearchString(location.search, filters),
    });
  };

  const areProcessInstanceStatesApplied = () => {
    const filters = getProcessInstanceFilters(location.search);

    return (
      filters.active ||
      filters.incidents ||
      filters.completed ||
      filters.canceled
    );
  };

  const setFilters = (filters: ProcessInstanceFilters) => {
    setFiltersToURL(filters);
  };

  const getFilters = () => {
    return getProcessInstanceFilters(location.search);
  };

  return {
    setFilters,
    getFilters,
    areProcessInstanceStatesApplied,
  };
};

export {useFilters};
