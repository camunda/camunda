/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {deployProcess, createInstances} from '../setup-utils';

const setup = async () => {
  const deployProcessResponse = await deployProcess([
    'ProcessInstanceMigration/orderProcessMigration_v_1.bpmn',
  ]);
  await deployProcess([
    'ProcessInstanceMigration/orderProcessMigration_v_2.bpmn',
  ]);

  if (deployProcessResponse.processes[0] === undefined) {
    throw new Error('Error deploying process');
  }

  const {version, processDefinitionKey, bpmnProcessId} =
    deployProcessResponse.processes[0];

  return {
    processInstances: await createInstances(
      'orderProcessMigration',
      version,
      10,
    ),
    processDefinitionKey,
    bpmnProcessId,
    version,
  };
};

export {setup};
