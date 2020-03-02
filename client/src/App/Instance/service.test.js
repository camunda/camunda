/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getProcessedSequenceFlows} from './service';

describe('instance service', () => {
  describe('getProcessedSequenceFlows', () => {
    it('should get distinct activity ids of processed sequence flows', () => {
      // given
      const processedSequenceFlows = [
        {
          id: '2251799813695632',
          workflowInstanceId: '2251799813693731',
          activityId: 'SequenceFlow_0drux68'
        },
        {
          id: '2251799813693749',
          workflowInstanceId: '2251799813693731',
          activityId: 'SequenceFlow_0j6tsnn'
        },
        {
          id: '2251799813695543',
          workflowInstanceId: '2251799813693731',
          activityId: 'SequenceFlow_1dwqvrt'
        },
        {
          id: '2251799813695629',
          workflowInstanceId: '2251799813693731',
          activityId: 'SequenceFlow_1fgekwd'
        },
        {
          id: '2251799813693750',
          workflowInstanceId: '2251799813693731',
          activityId: 'SequenceFlow_0j6tsnn'
        }
      ];

      // when
      const activityIds = getProcessedSequenceFlows(processedSequenceFlows);

      // then
      expect(activityIds.length).toBe(4);
      expect(activityIds).toEqual([
        'SequenceFlow_0drux68',
        'SequenceFlow_0j6tsnn',
        'SequenceFlow_1dwqvrt',
        'SequenceFlow_1fgekwd'
      ]);
    });
  });
});
