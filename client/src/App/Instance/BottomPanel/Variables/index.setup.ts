/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {MetaDataEntity} from 'modules/stores/flowNodeMetaData';

const mockVariables = [
  {
    id: '2251799813686037-clientNo',
    name: 'clientNo',
    value: '"CNT-1211132-0223222"',
    scopeId: '2251799813686037',
    processInstanceId: '2251799813686037',
    hasActiveOperation: false,
  },
  {
    id: '2251799813686037-mwst',
    name: 'mwst',
    value: '124.26',
    scopeId: '2251799813686037',
    processInstanceId: '2251799813686037',
    hasActiveOperation: false,
  },
  {
    id: '2251799813686037-mwst',
    name: 'active-operation-variable',
    value: '1',
    scopeId: '2251799813686037',
    processInstanceId: '2251799813686037',
    hasActiveOperation: true,
  },
] as const;

const mockMetaData: MetaDataEntity = {
  breadcrumb: [],
  flowNodeId: null,
  flowNodeInstanceId: '123',
  flowNodeType: 'start-event',
  instanceCount: null,
  instanceMetadata: null,
  incident: null,
  incidentCount: 0,
};

export {mockVariables, mockMetaData};
