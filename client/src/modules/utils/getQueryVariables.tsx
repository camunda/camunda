/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {FilterValues} from 'modules/constants/filterValues';
import {TaskStates} from 'modules/constants/taskStates';

const getQueryVariables = (filter: string, {username}: {username?: string}) => {
  switch (filter) {
    case FilterValues.ClaimedByMe: {
      return {
        assigned: true,
        assignee: username,
        state: TaskStates.Created,
      };
    }
    case FilterValues.Unclaimed: {
      return {
        assigned: false,
        state: TaskStates.Created,
      };
    }
    case FilterValues.Completed: {
      return {
        state: TaskStates.Completed,
      };
    }
    case FilterValues.AllOpen:
    default: {
      return {
        state: TaskStates.Created,
      };
    }
  }
};

export {getQueryVariables};
