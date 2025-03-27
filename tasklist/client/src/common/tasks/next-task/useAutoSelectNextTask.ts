/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useNavigate, useLocation} from 'react-router-dom';
import {pages} from 'common/routing';
import {encodeTaskOpenedRef} from 'common/tracking/reftags';
import {useTaskFilters} from 'v1/features/tasks/filters/useTaskFilters';

function useAutoSelectNextTask() {
  const {filter, sortBy} = useTaskFilters();
  const navigate = useNavigate();
  const location = useLocation();

  const navigateToTaskDetailsWithRef = (taskId: string) => {
    const search = new URLSearchParams(location.search);
    search.set(
      'ref',
      encodeTaskOpenedRef({
        by: 'auto-select',
        position: 0,
        filter,
        sorting: sortBy,
      }),
    );
    navigate({
      ...location,
      pathname: pages.taskDetails(taskId),
      search: search.toString(),
    });
  };

  return {
    goToTask: navigateToTaskDetailsWithRef,
  };
}

export {useAutoSelectNextTask};
