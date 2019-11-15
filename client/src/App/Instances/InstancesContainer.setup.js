/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export const mockLocalStorageProps = {
  getStateLocally: jest.fn(),
  storeStateLocally: jest.fn()
};

export const mockFullFilterWithoutWorkflow = {
  active: true,
  incidents: true,
  completed: true,
  canceled: true,
  ids: '424242, 434343',
  errorMessage: 'No%20data%20found%20for%20query%20$.foo.',
  startDate: '2018-12-28',
  endDate: '2018-12-28'
};

export const mockFullFilterWithWorkflow = {
  active: true,
  incidents: true,
  completed: true,
  canceled: true,
  ids: '424242, 434343',
  errorMessage: 'No%20data%20found%20for%20query%20$.foo.',
  startDate: '2018-12-28',
  endDate: '2018-12-28',
  workflow: 'demoProcess',
  version: 1
};
