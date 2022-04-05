/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {deploy, createSingleInstance, completeTask} from '../setup-utils';

export async function setup() {
  await deploy(['multiInstanceProcess.bpmn']);

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
    }
  );

  return {
    multiInstanceProcessInstance,
  };
}
