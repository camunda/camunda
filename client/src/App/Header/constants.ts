/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

// keys for values that fallback to the localState
export const localStateKeys = ['filter', 'filterCount'];

export const labels = {
  instances: 'Running Instances',
  filters: 'Filters',
  dashboard: 'Dashboard',
  incidents: 'Incidents',
  brand: 'Camunda Operate',
};

export const createTitle = (type: any, count: any) => {
  const titles = {
    brand: 'View Dashboard',
    dashboard: 'View Dashboard',
    instances: `View ${count} Running Instances`,
    filters: `View ${count} Instances in Filters`,
    incidents: `View ${count} Incidents`,
  };
  // @ts-expect-error ts-migrate(7053) FIXME: Element implicitly has an 'any' type because expre... Remove this comment to see the full error message
  return titles[type];
};

export const PATHNAME = {
  INSTANCES: '/instances',
  INSTANCE: '/instances/',
  DASHBOARD: '/',
};
