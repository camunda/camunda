/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {SequenceFlowsDto} from 'modules/api/processInstances/sequenceFlows';
import {getProcessedSequenceFlows} from './index';

describe('getProcessedSequenceFlows', () => {
  it('should get distinct activity ids of processed sequence flows', () => {
    // given
    const processedSequenceFlows = [
      {
        id: '2251799813695632',
        processInstanceId: '2251799813693731',
        activityId: 'SequenceFlow_0drux68',
      },
      {
        id: '2251799813693749',
        processInstanceId: '2251799813693731',
        activityId: 'SequenceFlow_0j6tsnn',
      },
      {
        id: '2251799813695543',
        processInstanceId: '2251799813693731',
        activityId: 'SequenceFlow_1dwqvrt',
      },
      {
        id: '2251799813695629',
        processInstanceId: '2251799813693731',
        activityId: 'SequenceFlow_1fgekwd',
      },
      {
        id: '2251799813693750',
        processInstanceId: '2251799813693731',
        activityId: 'SequenceFlow_0j6tsnn',
      },
    ];

    // when
    const activityIds = getProcessedSequenceFlows(processedSequenceFlows);

    // then
    expect(activityIds.length).toBe(4);
    expect(activityIds).toEqual([
      'SequenceFlow_0drux68',
      'SequenceFlow_0j6tsnn',
      'SequenceFlow_1dwqvrt',
      'SequenceFlow_1fgekwd',
    ]);
  });

  it('should return empty array id processed sequence flows is an empty array', () => {
    // given
    const processedSequenceFlows: SequenceFlowsDto = [];

    // when
    const activityIds = getProcessedSequenceFlows(processedSequenceFlows);

    // then
    expect(activityIds.length).toBe(0);
    expect(activityIds).toEqual([]);
  });
});
