/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

// keys for values that fallback to the localState
export const localStateKeys = [
  'filter',
  'filterCount',
  'selectionCount',
  'instancesInSelectionsCount'
];

export const labels = {
  instances: 'Running Instances',
  filters: 'Filters',
  dashboard: 'Dashboard',
  incidents: 'Incidents',
  selections: 'Selections',
  brand: 'Camunda Operate'
};

export const createTitle = (type, count) => {
  const titles = {
    brand: 'View Dashboard',
    dashboard: 'View Dashboard',
    instances: `View ${count} Running Instances`,
    filters: `View ${count} Instances in Filters`,
    incidents: `View ${count} Incidents`,
    selections: `View ${count} Selections`
  };
  return titles[type];
};

export const PATHNAME = {
  INSTANCES: '/instances',
  INSTANCE: '/instances/',
  DASHBOAD: '/'
};
