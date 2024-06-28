/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const mockListeners: ListenerEntity[] = [
  {
    listenerType: 'EXECUTION',
    listenerKey: 2344567895437448,
    state: 'ACTIVE',
    jobType: 'START',
    event: 'EVENT',
    time: '2024-01-01 17:51:16',
  },
  {
    listenerType: 'USER_TASK',
    listenerKey: 2344567895437448,
    state: 'ACTIVE',
    jobType: 'END',
    event: 'EVENT',
    time: '2024-01-02 18:11:12',
  },
];

export {mockListeners};
