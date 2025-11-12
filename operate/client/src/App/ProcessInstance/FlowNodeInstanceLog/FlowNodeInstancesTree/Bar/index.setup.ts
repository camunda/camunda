/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type FlowNodeInstance} from 'modules/stores/flowNodeInstance';

const mockStartNode: FlowNodeInstance = {
  type: 'START_EVENT',
  state: 'ACTIVE',
  id: '2251799813685264',
  flowNodeId: 'StartEvent_1',
  startDate: '2021-03-10T12:24:18.387+0000',
  endDate: '2021-03-10T12:24:18.399+0000',
  treePath: '',
  sortValues: [],
};

const mockStartEventBusinessObject = {
  id: 'StartEvent1',
  name: 'Start Event',
  $type: 'bpmn:StartEvent',
} as const;

export {mockStartNode, mockStartEventBusinessObject};
