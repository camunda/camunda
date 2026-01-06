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

import {variableFilterStore} from 'modules/stores/variableFilter';

const useFilters = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const setFiltersToURL = (filters: ProcessInstanceFilters) => {
    const {variableName, variableValues, ...filtersWithoutVariable} = filters;

    navigate({
      search: updateProcessFiltersSearchString(
        location.search,
        filtersWithoutVariable,
      ),
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
    if (
      filters.variableName !== undefined &&
      filters.variableValues !== undefined
    ) {
      variableFilterStore.setVariable({
        name: filters.variableName,
        values: filters.variableValues,
      });
    }
  };

  const getFilters = () => {
    return {
      ...getProcessInstanceFilters(location.search),
      ...(variableFilterStore.state.variable !== undefined
        ? {
            variableName: variableFilterStore.state.variable?.name,
            variableValues: variableFilterStore.state.variable?.values,
          }
        : {}),
    };
  };

  return {
    setFilters,
    getFilters,
    areProcessInstanceStatesApplied,
  };
};

export {useFilters};
