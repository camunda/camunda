/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const mockVariablesWithActiveOperation = [
  {
    id: '2251799813685360-test1',
    name: 'test1',
    value: '1',
    isPreview: false,
    hasActiveOperation: false,
    isFirst: true,
    sortValues: ['test1'],
  },
  {
    id: '2251799813685360-test2',
    name: 'test2',
    value: '3',
    isPreview: false,
    hasActiveOperation: true,
    isFirst: false,
    sortValues: ['test2'],
  },
];

export {mockVariablesWithActiveOperation};
