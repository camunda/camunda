/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {
  deployProcess,
  createInstances,
  createSingleInstance,
} from '../setup-utils';

async function createDemoInstances() {
  await deployProcess(['operationsProcessA.bpmn', 'operationsProcessB.bpmn']);

  const [singleOperationInstance, batchOperationInstances] = await Promise.all([
    createSingleInstance('operationsProcessA', 1),
    createInstances('operationsProcessB', 1, 10),
  ]);

  return {singleOperationInstance, batchOperationInstances};
}

export {createDemoInstances};
