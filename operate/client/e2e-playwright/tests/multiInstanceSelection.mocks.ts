/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {zeebeGrpcApi} from '../api/zeebe-grpc';

const {deployProcesses, completeTask, createSingleInstance} = zeebeGrpcApi;

const setup = async () => {
  await deployProcesses(['multiInstanceProcess.bpmn']);

  completeTask('multiInstanceProcessTaskA', false, {}, (job) => {
    return job.complete({i: Number(job.variables.i) + 1});
  });

  completeTask('multiInstanceProcessTaskB', true);

  const multiInstanceProcessInstance = await createSingleInstance(
    'multiInstanceProcess',
    1,
    {
      i: 0,
      loopCardinality: 5,
      clients: [0, 1, 2, 3, 4],
    },
  );

  return {
    multiInstanceProcessInstance,
  };
};

export {setup};
