/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export function incompatibleFilters(filterData, view) {
  const filters = filterData.map((filter) => filter.type);
  const bothExist = (arr) => arr.every((val) => filters.includes(val));

  return (
    bothExist(['completedInstancesOnly', 'runningInstancesOnly']) ||
    bothExist(['completedInstancesOnly', 'suspendedInstancesOnly']) ||
    bothExist(['canceledInstancesOnly', 'runningInstancesOnly']) ||
    bothExist(['canceledInstancesOnly', 'nonCanceledInstancesOnly']) ||
    bothExist(['canceledInstancesOnly', 'suspendedInstancesOnly']) ||
    bothExist(['nonSuspendedInstancesOnly', 'suspendedInstancesOnly']) ||
    bothExist(['endDate', 'runningInstancesOnly']) ||
    bothExist(['endDate', 'suspendedInstancesOnly']) ||
    ((view?.entity === 'flowNode' || view?.entity === 'userTask') &&
      (bothExist(['completedFlowNodesOnly', 'runningFlowNodesOnly']) ||
        bothExist(['canceledFlowNodesOnly', 'runningFlowNodesOnly']) ||
        bothExist(['completedOrCanceledFlowNodesOnly', 'runningFlowNodesOnly']) ||
        bothExist(['completedFlowNodesOnly', 'canceledFlowNodesOnly'])))
  );
}
