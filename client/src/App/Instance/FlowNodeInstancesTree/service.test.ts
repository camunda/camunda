/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {getNodeWithMetaData} from './service';

describe('FlowNodeInstancesTree.service', () => {
  describe('getNodeWithMetaData', () => {
    it('should return correct meta data for WORKFLOW type when instance id is same as current instance id', () => {
      const metaData = getNodeWithMetaData(
        {type: 'WORKFLOW', id: 'instanceId', activityId: 'activityId'},
        {
          name: 'start',
          activityId: 'activityId',
          type: {
            elementType: 'START',
          },
        },
        {id: 'instanceId', workflowName: 'workflowName'}
      );

      expect(metaData.type).toBe('WORKFLOW');
      expect(metaData.name).toBe('workflowName');
      expect(metaData.typeDetails.elementType).toBe('WORKFLOW');
    });

    it('should return correct meta data for WORKFLOW type when instance id is different than current instance id', () => {
      const metaData = getNodeWithMetaData(
        {type: 'WORKFLOW', id: 'instanceId', activityId: 'activityId'},
        {
          name: 'start',
          activityId: 'activityId',
          type: {
            elementType: 'START',
          },
        },
        {id: 'differentInstanceId', workflowName: 'workflowName'}
      );

      expect(metaData.type).toBe('WORKFLOW');
      expect(metaData.name).toBe('start');
      expect(metaData.typeDetails.elementType).toBe('WORKFLOW');
    });

    it('should return node activity id as meta data name', () => {
      const metaData = getNodeWithMetaData(
        {type: 'WORKFLOW', id: 'instanceId', activityId: 'activityId'},
        {
          name: undefined,
          type: {
            elementType: 'START',
          },
        },
        {id: 'differentInstanceId', workflowName: 'workflowName'}
      );

      expect(metaData.type).toBe('WORKFLOW');
      expect(metaData.name).toBe('activityId');
      expect(metaData.typeDetails.elementType).toBe('WORKFLOW');
    });

    it('should return correct meta data for MULTI_INSTANCE_BODY type', () => {
      const metaData = getNodeWithMetaData(
        {
          type: 'MULTI_INSTANCE_BODY',
          id: 'instanceId',
          activityId: 'activityId',
        },
        {
          name: 'start',
          activityId: 'activityId',
          type: {
            elementType: 'START',
          },
        },
        {id: 'instanceId', workflowName: 'workflowName'}
      );

      expect(metaData.type).toBe('MULTI_INSTANCE_BODY');
      expect(metaData.name).toBe('workflowName');
      expect(metaData.typeDetails.elementType).toBe('MULTI_INSTANCE_BODY');
    });

    it('should return correct meta data for some other type', () => {
      const metaData = getNodeWithMetaData(
        {type: 'OTHER', id: 'instanceId', activityId: 'activityId'},
        {
          name: 'start',
          type: {
            elementType: 'START',
          },
        },
        {id: 'instanceId', workflowName: 'workflowName'}
      );

      expect(metaData.type).toBe('OTHER');
      expect(metaData.name).toBe('workflowName');
      expect(metaData.typeDetails.elementType).toBe('START');
    });
  });
});
