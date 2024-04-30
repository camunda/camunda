/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ZBWorkerTaskHandler} from 'zeebe-node';
import {
  deployProcess,
  createSingleInstance,
  completeTask,
} from '../setup-utils';

export async function setup() {
  await deployProcess(['manyFlowNodeInstancesProcess.bpmn', 'bigProcess.bpmn']);

  const manyFlowNodeInstancesProcessInstance = await createSingleInstance(
    'manyFlowNodeInstancesProcess',
    1,
    {i: 0, loopCardinality: 130},
  );

  const bigProcessInstance = await createSingleInstance('bigProcess', 1, {
    i: 0,
    loopCardinality: 2,
    clients: Array.from(Array(250).keys()),
  });

  const incrementTaskHandler: ZBWorkerTaskHandler = (job) => {
    return job.complete({...job.variables, i: job.variables.i + 1});
  };
  completeTask('increment', false, {}, incrementTaskHandler, 50);

  const taskBHandler: ZBWorkerTaskHandler = (job) => {
    return job.complete();
  };
  completeTask('bigProcessTaskB', false, {}, taskBHandler);

  return {
    manyFlowNodeInstancesProcessInstance,
    bigProcessInstance,
  };
}
