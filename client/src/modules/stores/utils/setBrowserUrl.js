/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getFilterQueryString, isVariableEmpty} from 'modules/utils/filter';

const setBrowserUrl = (history, location, filter, groupedWorkflows, name) => {
  let workflowName;
  let urlFilterValues = {...filter};

  if (name !== undefined) {
    workflowName = name;
  } else if (filter.workflow) {
    const {name, bpmnProcessId} = groupedWorkflows[filter.workflow];
    workflowName = name || bpmnProcessId;
  } else {
    workflowName = '';
  }

  if (isVariableEmpty(urlFilterValues.variable)) {
    urlFilterValues.variable = null;
  }

  return history.push({
    pathname: location.pathname,
    search: getFilterQueryString(urlFilterValues, workflowName),
  });
};

export {setBrowserUrl};
