/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {bigFormProcessXml} from './bpmn';

const bigFormProcess = {
  id: '2251799813685255',
  name: 'Big form process',
  bpmnProcessId: 'bigFormProcess',
  sortValues: null,
  version: 1,
  startEventFormId: null,
  tenantId: '<default>',
  bpmnXml: bigFormProcessXml,
};

const noStartFormProcessQueryResult = [
  {
    id: '2251799813685285',
    name: 'multipleVersions',
    bpmnProcessId: 'multipleVersions',
    version: 1,
    startEventFormId: null,
  },
  {
    id: '2251799813685271',
    name: 'Order process',
    bpmnProcessId: 'orderProcess',
    version: 1,
    startEventFormId: null,
  },
];

const processWithStartFormQueryResult = [
  {
    id: '2251799813685285',
    name: 'startForm',
    bpmnProcessId: 'startForm',
    version: 1,
    startEventFormId: 'startFormForm',
  },
  {
    id: '2251799813685271',
    name: 'Order process',
    bpmnProcessId: 'orderProcess',
    version: 1,
    startEventFormId: null,
  },
];

export {
  bigFormProcess,
  noStartFormProcessQueryResult,
  processWithStartFormQueryResult,
};
