/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {getModalHeadline} from './getModalHeadline';

describe('getModalHeadline', () => {
  it('should get title for single instance', () => {
    expect(
      getModalHeadline({
        flowNodeName: 'Start Event',
        metaData: {
          breadcrumb: [],
          flowNodeId: 'StartEvent_1',
          flowNodeInstanceId: '2251799813685870',
          instanceCount: 1,
          flowNodeType: 'START_EVENT',
          instanceMetadata: null,
          incident: null,
          incidentCount: 0,
        },
      })
    ).toEqual('Flow Node "Start Event" Metadata');
  });

  it('should get title for single peter case instance', () => {
    expect(
      getModalHeadline({
        flowNodeName: 'Task A',
        metaData: {
          breadcrumb: [{flowNodeId: '', flowNodeType: 'MULTI_INSTANCE_BODY'}],
          flowNodeId: 'Task_A',
          flowNodeInstanceId: '2251799813685870',
          instanceCount: 1,
          flowNodeType: 'MULTI_INSTANCE_BODY',
          instanceMetadata: null,
          incident: null,
          incidentCount: 0,
        },
      })
    ).toEqual('Flow Node "Task A" Instance 2251799813685870 Metadata');
  });
});
