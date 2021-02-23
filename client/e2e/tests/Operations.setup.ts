/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {deploy, createInstances, createSingleInstance} from '../setup-utils';
import {createOperation} from './api';

async function setup() {
  await deploy([
    './e2e/tests/resources/operationsProcessA.bpmn',
    './e2e/tests/resources/operationsProcessB.bpmn',
  ]);

  const [singleOperationInstance, batchOperationInstances] = await Promise.all([
    createSingleInstance('operationsProcessA', 1),
    createInstances('operationsProcessB', 1, 10),
  ]);

  [...new Array(40)].forEach(() => {
    createOperation({
      id: singleOperationInstance.workflowInstanceKey,
      operationType: 'RESOLVE_INCIDENT',
    });
  });

  return {singleOperationInstance, batchOperationInstances};
}

export {setup};
