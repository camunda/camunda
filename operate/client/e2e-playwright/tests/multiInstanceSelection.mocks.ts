/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  deployProcess,
  createSingleInstance,
  completeTask,
} from '../setup-utils';

const setup = async () => {
  await deployProcess(['multiInstanceProcess.bpmn']);

  completeTask('multiInstanceProcessTaskA', false, {}, (job) => {
    return job.complete({...job.variables, i: job.variables.i + 1});
  });

  completeTask('multiInstanceProcessTaskB', true);

  const multiInstanceProcessInstance = await createSingleInstance(
    'multiInstanceProcess',
    1,
    {
      i: 0,
      loopCardinality: 5,
      clients: new Array(5),
    },
  );

  return {
    multiInstanceProcessInstance,
  };
};

export {setup};
