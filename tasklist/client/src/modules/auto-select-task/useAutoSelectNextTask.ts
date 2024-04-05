/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {useNavigate, useLocation} from 'react-router-dom';
import {pages} from 'modules/routing';
import {encodeTaskOpenedRef} from 'modules/utils/reftags';
import {useTaskFilters} from '../hooks/useTaskFilters';

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
