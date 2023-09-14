/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ProcessInstance} from 'modules/types';

const processInstances: ProcessInstance[] = [
  {
    id: '1',
    process: {
      id: '1',
      name: 'Process 1',
      bpmnProcessId: 'Process_1',
      version: 1,
      startEventFormId: null,
      sortValues: ['1'],
    },
    creationDate: '2021-01-01T00:00:00.000+00:00',
    isFirst: true,
    state: 'completed',
    sortValues: ['1', '2'],
  },
  {
    id: '2',
    process: {
      id: '1',
      name: 'Process 1',
      bpmnProcessId: 'Process_1',
      version: 1,
      startEventFormId: null,
      sortValues: ['1'],
    },
    creationDate: '2021-01-01T00:00:00.000+00:00',
    isFirst: false,
    state: 'pending',
    sortValues: ['1', '2'],
  },
  {
    id: '3',
    process: {
      id: '1',
      name: 'Process 1',
      bpmnProcessId: 'Process_1',
      version: 1,
      startEventFormId: null,
      sortValues: ['1'],
    },
    creationDate: '2021-01-01T00:00:00.000+00:00',
    isFirst: false,
    state: 'failed',
    sortValues: ['1', '2'],
  },
  {
    id: '4',
    process: {
      id: '1',
      name: 'Process 1',
      bpmnProcessId: 'Process_1',
      version: 1,
      startEventFormId: null,
      sortValues: ['1'],
    },
    creationDate: '2021-01-01T00:00:00.000+00:00',
    isFirst: false,
    state: 'terminated',
    sortValues: ['1', '2'],
  },
];

export {processInstances};
