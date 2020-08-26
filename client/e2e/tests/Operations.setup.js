/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {deploy, createInstances} from '../setup-utils';

async function setup() {
  await deploy([
    './tests/resources/operationsProcess_v_1.bpmn',
    './tests/resources/operationsProcess_v_2.bpmn',
  ]);

  const instancesToCancel = await createInstances(
    'operationsProcess_v_1',
    1,
    1
  );
  const instancesToRetry = await createInstances(
    'operationsProcess_v_2',
    1,
    10
  );

  return {instancesToCancel, instancesToRetry};
}

export {setup};
