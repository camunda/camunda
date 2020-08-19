/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {sortData} from './service';
import {createIncident} from 'modules/testUtils';

const data = [
  createIncident({
    id: '1',
    creationTime: '2017-03-14T15:17:21.296+0000',
    errorType: 'No more retries left',
    flowNodeName: 'Task B',
  }),
  createIncident({
    id: '2',
    creationTime: '2018-12-14T15:17:32.919+0000',
    errorType: 'I/O error',
    flowNodeName: 'Task A',
  }),

  createIncident({
    id: '3',
    creationTime: '2018-03-14T15:17:32.919+0000',
    errorType: 'I/O error',
    flowNodeName: 'Task C',
  }),
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
