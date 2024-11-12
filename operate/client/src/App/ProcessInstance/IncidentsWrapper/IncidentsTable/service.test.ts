/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {sortIncidents} from './service';
import {createIncident} from 'modules/testUtils';
import {SORT_ORDER} from 'modules/constants';

const incidents = [
  {
    ...createIncident({
      id: '1',
      creationTime: '2017-03-14T15:17:21.296+0000',
      errorType: {id: 'NO_MORE_RETRIES', name: 'No more retries left'},
    }),
    isSelected: false,
    flowNodeName: 'Task B',
  },
  {
    ...createIncident({
      id: '2',
      creationTime: '2018-12-14T15:17:32.919+0000',
      errorType: {id: 'IO_ERROR', name: 'I/O error'},
    }),
    isSelected: false,
    flowNodeName: 'Task A',
  },
  {
    ...createIncident({
      id: '3',
      creationTime: '2018-03-14T15:17:32.919+0000',
      errorType: {id: 'IO_ERROR', name: 'I/O error'},
    }),
    isSelected: false,
    flowNodeName: 'Task C',
  },
];

describe('sortIncidents', () => {
  it('should sort by secondary key', () => {
    const sorted = sortIncidents(incidents, 'errorType', SORT_ORDER.ASC);

    expect(sorted[0]?.id).toEqual('2');
    expect(sorted[1]?.id).toEqual('3');
    expect(sorted[2]?.id).toEqual('1');
  });

  it('should sort descending', () => {
    const sorted = sortIncidents(incidents, 'flowNodeName', SORT_ORDER.DESC);

    expect(sorted[0]?.id).toEqual('3');
    expect(sorted[1]?.id).toEqual('1');
    expect(sorted[2]?.id).toEqual('2');
  });

  it('should sort correctly the dates', () => {
    const sorted = sortIncidents(incidents, 'creationTime', SORT_ORDER.ASC);

    expect(sorted[0]?.id).toEqual('1');
    expect(sorted[1]?.id).toEqual('3');
    expect(sorted[2]?.id).toEqual('2');
  });
});
