export function createMapOfInstances(workflowInstances) {
  const transformedInstances = workflowInstances.reduce((acc, instance) => {
    return {
      [instance.id]: instance,
      ...acc
    };
  }, {});
  return new Map(Object.entries(transformedInstances));
}

export function getInstancesIdsFromSelections(selections) {
  const idsSet = selections.reduce((acc, {instancesMap}) => {
    return new Set([...acc, ...[...instancesMap.keys()]]);
  }, new Set());

  return [...idsSet];
}
