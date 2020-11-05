/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {deploy, createInstances, createSingleInstance} from '../setup-utils';

export async function setup() {
  await deploy([
    './e2e/tests/resources/Filters/processWithMultipleVersions_v_1.bpmn',
  ]);
  await deploy([
    './e2e/tests/resources/Filters/processWithMultipleVersions_v_2.bpmn',
  ]);
  await deploy(['./e2e/tests/resources/Filters/processWithAnError.bpmn']);

  await createInstances('processWithMultipleVersions', 1, 1);
  await createInstances('processWithMultipleVersions', 2, 1);

  await createInstances('processWithAnError', 1, 1);

  await deploy(['./e2e/tests/resources/orderProcess_v_1.bpmn']);

  const instanceToCancel = await createSingleInstance('orderProcess', 1);
  await createSingleInstance('orderProcess', 1, {
    filtersTest: 123,
  });

  const instanceToCancelForOperations = await createSingleInstance(
    'orderProcess',
    1
  );

  return {instanceToCancel, instanceToCancelForOperations};
}
