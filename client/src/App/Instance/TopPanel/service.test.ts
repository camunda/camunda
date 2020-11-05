/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getCurrentMetadata} from './service';
import {flowNodeIdToFlowNodeInstanceMap, events} from './service.setup';

describe('service', () => {
  describe('getCurrentMetaData', () => {
    it('should get metaData when multiple nodes are selected', () => {
      const metaData = getCurrentMetadata(null, null, [1, 2, 3], null, true);

      expect(metaData).toEqual({isMultiRowPeterCase: true, instancesCount: 3});
    });

    it('should get metaData when single node is selected', () => {
      const metaData = getCurrentMetadata(
        events,
        'neverFails',
        ['2251799813685614'],
        flowNodeIdToFlowNodeInstanceMap,
        false
      );

      expect(metaData).toEqual({
        parentId: '2251799813685607',
        data: {
          activityInstanceId: '2251799813685614',
          jobType: 'neverFails',
          jobRetries: 3,
          jobWorker: 'operate',
          jobDeadline: '2020-08-20T10:06:46.053+0000',
          jobCustomHeaders: {},
          jobId: '2251799813685618',
          startDate: '12 Dec 2018 00:00:00',
          endDate: '--',
        },
      });
    });
  });
});
