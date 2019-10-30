/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {LOADING_STATE, SUBSCRIPTION_TOPIC} from 'modules/constants';

export const mockDataManager = () => {
  return {
    publish: jest.fn(
      ({subscription, state = LOADING_STATE.LOADED, response, staticContent}) =>
        subscription({state, response, staticContent})
    ),
    poll: {clear: jest.fn(), start: jest.fn().mockImplementation(cb => cb())},
    update: jest.fn(),
    subscribe: jest.fn(),
    unsubscribe: jest.fn(),
    applyOperation: jest.fn(),
    getVariables: jest.fn(),
    getEvents: jest.fn(),
    getIncidents: jest.fn(),
    getWorkflowXML: jest.fn(),
    getWorkflowInstance: jest.fn(),
    getWorkflowInstances: jest.fn(),
    getActivityInstancesTreeData: jest.fn(),
    getWorkflowInstancesStatistics: jest.fn(),
    getWorkflowInstancesByIds: jest.fn(),
    getWorkflowInstancesBySelection: jest.fn(),
    getWorkflowCoreStatistics: jest.fn()
  };
};

export const mockModule = dataManagerInstance => {
  jest.mock('modules/DataManager/core');
  dataManagerInstance.mockImplementation(mockDataManager);
};

export const constants = {SUBSCRIPTION_TOPIC, LOADING_STATE};
