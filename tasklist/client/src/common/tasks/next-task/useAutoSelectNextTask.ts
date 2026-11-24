/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useNavigate, useLocation} from 'react-router';
import {pages} from 'common/routing';

function useAutoSelectNextTask() {
  const navigate = useNavigate();
  const location = useLocation();

  const navigateToTaskDetails = (taskId: string) => {
    navigate({
      ...location,
      pathname: pages.taskDetails(taskId),
    });
  };

  return {
    goToTask: navigateToTaskDetails,
  };
}

export {useAutoSelectNextTask};
