/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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

export {mockWithSingleVersion, mockWithMultipleVersions};
