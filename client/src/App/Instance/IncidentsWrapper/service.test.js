/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {sortData} from './service';

const data = [
  {
    creationTime: '2017-03-14T15:17:21.296+0000',
    errorMessage: 'No more retries left.',
    errorType: 'No more retries left',
    flowNodeId: 'taskB',
    flowNodeInstanceId: '2251799813689601',
    flowNodeName: 'Task B',
    hasActiveOperation: false,
    id: '1',
    jobId: '2251799813689609',
    lastOperation: null
  },
  {
    creationTime: '2018-12-14T15:17:32.919+0000',
    errorMessage: 'I/O error',
    errorType: 'I/O error',
    flowNodeId: 'taskA',
    flowNodeInstanceId: '2251799813689601',
    flowNodeName: 'Task A',
    hasActiveOperation: false,
    id: '2',
    jobId: '2251799813707976',
    lastOperation: null
  },
  {
    creationTime: '2018-03-14T15:17:32.919+0000',
    errorMessage: 'I/O error',
    errorType: 'I/O error',
    flowNodeId: 'taskC',
    flowNodeInstanceId: '2251799813689601',
    flowNodeName: 'Task C',
    hasActiveOperation: false,
    id: '3',
    jobId: '2251799813707976',
    lastOperation: null
  }
];

describe('sortData', () => {
  it('should sort by secondary key', () => {
    const sorted = sortData(data, 'errorType', 'ASC');

    expect(sorted[0].id).toEqual('2');
    expect(sorted[1].id).toEqual('3');
    expect(sorted[2].id).toEqual('1');
  });

  it('should sort descending', () => {
    const sorted = sortData(data, 'flowNodeName', 'DESC');

    expect(sorted[0].id).toEqual('2');
    expect(sorted[1].id).toEqual('1');
    expect(sorted[2].id).toEqual('3');
  });

  it('should sort correctly the dates', () => {
    const sorted = sortData(data, 'creationTime', 'ASC');

    expect(sorted[0].id).toEqual('1');
    expect(sorted[1].id).toEqual('3');
    expect(sorted[2].id).toEqual('2');
  });
});
