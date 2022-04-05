/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {deploy, createInstances, createSingleInstance} from '../setup-utils';

const setup = async () => {
  await deploy(['Filters/processWithMultipleVersions_v_1.bpmn']);
  await deploy(['Filters/processWithMultipleVersions_v_2.bpmn']);
  await deploy(['Filters/processWithAnError.bpmn']);

  await createInstances('processWithMultipleVersions', 1, 1);
  await createInstances('processWithMultipleVersions', 2, 1);

  await createInstances('processWithAnError', 1, 1);

  await deploy(['orderProcess_v_1.bpmn']);

  const instanceToCancel = await createSingleInstance('orderProcess', 1);
  await createSingleInstance('orderProcess', 1, {
    filtersTest: 123,
  });

  const instanceToCancelForOperations = await createSingleInstance(
    'orderProcess',
    1
  );

  await deploy(['callActivityProcess.bpmn', 'calledProcess.bpmn']);

  const callActivityProcessInstance = await createSingleInstance(
    'CallActivityProcess',
    1
  );

  return {
    instanceToCancel,
    instanceToCancelForOperations,
    callActivityProcessInstance,
  };
};

export {setup};
