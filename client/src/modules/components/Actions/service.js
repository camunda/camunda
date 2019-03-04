/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {STATE, OPERATION_TYPE} from 'modules/constants';

/**
 * @returns an query object based on the type of operation to perform
 * @param {*} operationType a constants specifying the type of action
 * @param {*} instanceId string value specifying the instance id
 */
export const wrapIdinQuery = (operationType, instance) => {
  let basicQuery = {ids: [instance.id]};

  const queryTypes = {
    [OPERATION_TYPE.CANCEL_WORKFLOW_INSTANCE]: isWithIncident(instance)
      ? {running: true, incidents: true}
      : {running: true, active: true},
    [OPERATION_TYPE.RESOLVE_INCIDENT]: {running: true, incidents: true}
  };

  return [{...basicQuery, ...queryTypes[operationType]}];
};

/**
 * @returns a boolean showing if the current instance has an incident
 * @param {*} instance object with complete instance data
 */
export const isWithIncident = instance => {
  return instance.state === STATE.INCIDENT;
};

/**
 * @returns a boolean showing if the current instance is running.
 * @param {*} instance object with complete instance data
 */
export const isRunning = instance => {
  const state = instance.state;
  return state !== STATE.COMPLETED && state !== STATE.CANCELED;
};
