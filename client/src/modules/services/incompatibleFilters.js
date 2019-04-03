/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export function incompatibleFilters(filterData) {
  const filters = filterData.map(filter => filter.type);

  return (
    ['completedInstancesOnly', 'runningInstancesOnly'].every(val => filters.includes(val)) ||
    ['canceledInstancesOnly', 'runningInstancesOnly'].every(val => filters.includes(val)) ||
    ['endDate', 'runningInstancesOnly'].every(val => filters.includes(val))
  );
}
