/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
