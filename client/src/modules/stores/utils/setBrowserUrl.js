/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getFilterQueryString} from 'modules/utils/filter';

const setBrowserUrl = (history, location, filter, groupedWorkflows, name) => {
  let workflowName;

  if (name !== undefined) {
    workflowName = name;
  } else if (filter.workflow) {
    const {name, bpmnProcessId} = groupedWorkflows[filter.workflow];
    workflowName = name || bpmnProcessId;
  } else {
    workflowName = '';
  }
  return history.push({
    pathname: location.pathname,
    search: getFilterQueryString(filter, workflowName),
  });
};

export {setBrowserUrl};
