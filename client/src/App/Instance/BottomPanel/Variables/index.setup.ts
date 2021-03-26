/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const mockVariables = [
  {
    id: '2251799813686037-clientNo',
    name: 'clientNo',
    value: '"CNT-1211132-0223222"',
    scopeId: '2251799813686037',
    processInstanceId: '2251799813686037',
    hasActiveOperation: false,
  },
  {
    id: '2251799813686037-mwst',
    name: 'mwst',
    value: '124.26',
    scopeId: '2251799813686037',
    processInstanceId: '2251799813686037',
    hasActiveOperation: false,
  },
  {
    id: '2251799813686037-mwst',
    name: 'active-operation-variable',
    value: '1',
    scopeId: '2251799813686037',
    processInstanceId: '2251799813686037',
    hasActiveOperation: true,
  },
];

export {mockVariables};
