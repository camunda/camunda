/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {createProcess, createInstanceByProcess} from 'modules/testUtils';

const mockWithSingleVersion = [
  createInstanceByProcess({
    processes: [createProcess()],
  }),
];

const mockWithMultipleVersions = [
  createInstanceByProcess({
    instancesWithActiveIncidentsCount: 65,
    activeInstancesCount: 136,
    processName: 'Order process',
    bpmnProcessId: 'orderProcess',
    processes: [
      createProcess({name: 'First Version', version: 1}),
      createProcess({name: 'Second Version', version: 2}),
    ],
  }),
];

const mockErrorResponse = {error: 'an error occured'};

export {mockWithSingleVersion, mockWithMultipleVersions, mockErrorResponse};
