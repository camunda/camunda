/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

export const requestDetails = {params: {query: {}}, apiCall: jest.fn()};
export const requestName = 'someName';

export const requests = [
  {name: 'someName', details: {}},
  {name: 'someOtherName', details: {}}
];

export const requestNames = ['someName', 'someOtherName'];

export const requestDetailsByName = {
  someName: {},
  someOtherName: {}
};

export const newRequestDetails = {params: {query: {}}, apiCall: jest.fn()};
