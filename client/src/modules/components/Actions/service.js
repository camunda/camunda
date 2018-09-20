import {getInstanceState} from 'modules/utils/instance';
import {INSTANCE_STATE, OPERATION_TYPE} from 'modules/constants';

/**
 * @returns an query object based on the type of operation to perform
 * @param {*} operationType a constants specifying the type of action
 * @param {*} instanceId string value specifying the instance id
 */
export const wrapIdinQuery = (operationType, instance) => {
  let basicQuery = {ids: [instance.id]};

  const queryTypes = {
    [OPERATION_TYPE.CANCEL]: isWithIncident(instance)
      ? {running: true, incidents: true}
      : {running: true, active: true},
    [OPERATION_TYPE.UPDATE_RETRIES]: {running: true, incidents: true}
  };

  return [{...basicQuery, ...queryTypes[operationType]}];
};

/**
 * @returns a boolean showing if the current instance has an incident
 * @param {*} instance object with complete instance data
 */
export const isWithIncident = instance =>
  getInstanceState(instance) === INSTANCE_STATE.INCIDENT;

/**
 * @returns a boolean showing if the current instance is running.
 * @param {*} instance object with complete instance data
 */
export const isRunning = instance => {
  const state = getInstanceState(instance);
  return (
    state !== INSTANCE_STATE.COMPLETED && state !== INSTANCE_STATE.CANCELED
  );
};
