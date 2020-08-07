/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {deploy, createInstances, createSingleInstance} from '../setup-utils';

export async function setup() {
  await deploy([
    './tests/resources/withoutInstancesProcess_v_1.bpmn',
    './tests/resources/withoutIncidentsProcess_v_1.bpmn',
    './tests/resources/onlyIncidentsProcess_v_1.bpmn',
    './tests/resources/orderProcess_v_1.bpmn',
  ]);
  await deploy([
    './tests/resources/withoutInstancesProcess_v_2.bpmn',
    './tests/resources/withoutIncidentsProcess_v_2.bpmn',
    './tests/resources/onlyIncidentsProcess_v_2.bpmn',
  ]);

  await createInstances('withoutIncidentsProcess', 1, 4);
  await createInstances('withoutIncidentsProcess', 2, 8);

  await createInstances('onlyIncidentsProcess', 1, 10);
  await createInstances('onlyIncidentsProcess', 2, 5);

  await deploy(['./tests/resources/orderProcess_v_1.bpmn']);

  await createInstances('orderProcess', 1, 10);

  await deploy(['./tests/resources/processWithAnIncident.bpmn']);

  await createSingleInstance('processWithAnIncident', 1);
}
