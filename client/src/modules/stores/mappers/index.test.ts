/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getProcessedSequenceFlows, createNodeMetaDataMap} from './index';

describe('mappers', () => {
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
      const processedSequenceFlows: any = [];

      // when
      const activityIds = getProcessedSequenceFlows(processedSequenceFlows);

      // then
      expect(activityIds.length).toBe(0);
      expect(activityIds).toEqual([]);
    });
  });

  describe('createNodeMetaDataMap', () => {
    it('should get input output mappings', () => {
      const metaData = createNodeMetaDataMap({
        activity1: {
          id: '1',
          name: 'element1',
          $type: 'type1',
          $instanceOf: () => true,
        },
        activity2: {
          id: '2',
          name: 'element2',
          $type: 'type2',
          $instanceOf: () => true,
        },
        activity3: {
          id: '3',
          name: 'element3',
          $type: 'type3',
          extensionElements: {
            values: [{$type: 'value1'}],
          },
          $instanceOf: () => true,
        },
        activity4: {
          id: '4',
          name: 'element4',
          $type: 'type4',
          extensionElements: {
            values: [{$type: 'zeebe:ioMapping'}],
          },
          $instanceOf: () => true,
        },
        activity5: {
          id: '5',
          name: 'element5',
          $type: 'type5',
          extensionElements: {
            values: [
              {
                $type: 'zeebe:ioMapping',
                $children: [
                  {$type: 'zeebe:input', source: 'source1', target: 'target1'},
                  {$type: 'zeebe:input', source: 'source2', target: 'target2'},
                  {$type: 'zeebe:output', source: 'source3', target: 'target3'},
                ],
              },
            ],
          },
          $instanceOf: () => true,
        },
      });

      expect(metaData['activity1']?.type.inputMappings).toEqual([]);
      expect(metaData['activity1']?.type.outputMappings).toEqual([]);

      expect(metaData['activity2']?.type.inputMappings).toEqual([]);
      expect(metaData['activity2']?.type.outputMappings).toEqual([]);

      expect(metaData['activity3']?.type.inputMappings).toEqual([]);
      expect(metaData['activity3']?.type.outputMappings).toEqual([]);

      expect(metaData['activity4']?.type.inputMappings).toEqual([]);
      expect(metaData['activity4']?.type.outputMappings).toEqual([]);

      expect(metaData['activity5']?.type.inputMappings).toEqual([
        {source: 'source1', target: 'target1'},
        {source: 'source2', target: 'target2'},
      ]);
      expect(metaData['activity5']?.type.outputMappings).toEqual([
        {source: 'source3', target: 'target3'},
      ]);
    });
  });
});
