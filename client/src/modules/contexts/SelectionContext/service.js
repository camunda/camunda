import {VISIBLE_INSTANCES_IN_SELECTION} from 'modules/constants';
export function createMapOfInstances(workflowInstances) {
  const transformedInstances = workflowInstances.reduce((acc, instance) => {
    return {
      [instance.id]: instance,
      ...acc
    };
  }, {});
  return new Map(Object.entries(transformedInstances));
}

export function updateMapOfInstances(workflowInstances, oldInstanceMap) {
  const updatedInstanceMap = new Map([...oldInstanceMap]);
  workflowInstances.forEach(instance => {
    if (updatedInstanceMap.size < VISIBLE_INSTANCES_IN_SELECTION) {
      updatedInstanceMap.set(instance.id, instance);
    }
  });
  return updatedInstanceMap;
}

export function getInstancesIdsFromSelections(selections) {
  const idsSet = selections.reduce((acc, {instancesMap}) => {
    return new Set([...acc, ...[...instancesMap.keys()]]);
  }, new Set());

  return [...idsSet];
}
