import {ACTIVE_OPERATION_STATES} from 'modules/constants';

export function getInstancesWithActiveOperations(instances) {
  let list = [];

  instances.forEach(instance => {
    const activeOperation = instance.operations.find(operation =>
      ACTIVE_OPERATION_STATES.includes(operation.state)
    );

    if (activeOperation) {
      list.push(instance);
    }
  });

  return list;
}
