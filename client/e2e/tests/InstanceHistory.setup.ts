/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {ZBWorkerTaskHandler} from 'zeebe-node';
import {deploy, createSingleInstance, completeTask} from '../setup-utils';

export async function setup() {
  await deploy(['./e2e/tests/resources/manyFlowNodeInstancesProcess.bpmn']);

  const processInstance = await createSingleInstance(
    'manyFlowNodeInstancesProcess',
    1,
    {i: 0, loopCardinality: 60}
  );

  const taskHandler: ZBWorkerTaskHandler = (job) => {
    return job.complete({...job.variables, i: job.variables.i + 1});
  };

  completeTask('increment', false, {}, taskHandler);

  return {
    processInstance,
  };
}
