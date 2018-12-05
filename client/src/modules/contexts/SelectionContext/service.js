export const createMapOfInstances = workflowInstances => {
  const transformedInstances = workflowInstances.reduce((acc, instance) => {
    return {
      [instance.id]: instance,
      ...acc
    };
  }, {});
  return new Map(Object.entries(transformedInstances));
};

export const getPayloadtoFetchInstancesById = IdsOfInstancesInSelections => {
  let query = {
    ids: [...IdsOfInstancesInSelections],
    running: true,
    active: true,
    canceled: true,
    completed: true,
    finished: true,
    incidents: true
  };

  return {queries: [query]};
};
