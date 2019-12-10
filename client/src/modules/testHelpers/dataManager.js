/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {DataManager} from 'modules/DataManager/core';
import {LOADING_STATE, SUBSCRIPTION_TOPIC} from 'modules/constants';

jest.mock('modules/DataManager/core');
jest.mock('modules/utils/bpmn');

const mockDataManager = () => {
  let subscription = {};
  return {
    publish: jest.fn(
      ({subscription, state = LOADING_STATE.LOADED, response, staticContent}) =>
        subscription({state, response, staticContent})
    ),
    // THIS IS A TEMPORARY ENDPOINT,
    publishing: jest.fn(),
    poll: {clear: jest.fn(), start: jest.fn().mockImplementation(cb => cb())},
    update: jest.fn(),
    subscribe: jest.fn().mockImplementation(subs => {
      subscription = subs;
    }),
    subscriptions: jest.fn(() => subscription),
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

export const createMockDataManager = () => {
  DataManager.mockImplementation(mockDataManager);

  return new DataManager();
};

export const constants = {SUBSCRIPTION_TOPIC, LOADING_STATE};
