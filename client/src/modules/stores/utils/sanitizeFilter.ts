/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {DEFAULT_FILTER} from 'modules/constants';
import {getWorkflowByVersion} from 'modules/utils/filter';

const sanitizeFilter = (filter: any, groupedWorkflows?: any) => {
  if (!filter) {
    return DEFAULT_FILTER;
  }

  const {workflow, version, activityId, ...otherFilters} = filter;

  if (activityId && !(workflow && version)) {
    return otherFilters;
  }

  const isWorkflowValid = groupedWorkflows[workflow] !== undefined;
  if (!isWorkflowValid) {
    return otherFilters;
  }

  if (version === 'all' && activityId) {
    return {...otherFilters, workflow, version};
  }

  const workflowByVersion = getWorkflowByVersion(
    groupedWorkflows[workflow],
    version
  );

  if (workflowByVersion === undefined) {
    return otherFilters;
  }

  return filter;
};

export {sanitizeFilter};
