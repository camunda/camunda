/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const mockDecisionInstances = {
  decisionInstances: [
    {
      id: '2251799813689541',
      name: 'test decision instance 1',
      version: 1,
      evaluationTime: '2022-02-07T10:01:51.293+0000',
      processInstanceId: '2251799813689544',
      state: 'COMPLETED',
    },
    {
      id: '2251799813689542',
      name: 'test decision instance 2',
      version: 1,
      evaluationTime: '2022-02-07T10:01:51.293+0000',
      processInstanceId: '2251799813689544',
      state: 'FAILED',
    },
  ],
} as const;

export {mockDecisionInstances};
