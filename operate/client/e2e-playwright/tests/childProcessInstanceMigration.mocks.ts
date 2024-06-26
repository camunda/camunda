/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {deployProcess, createInstances} from '../setup-utils';

const setup = async () => {
  const deployProcessResponse = await deployProcess([
    'ChildProcessInstanceMigration/callActivityParentProcess.bpmn',
    'ChildProcessInstanceMigration/childProcess_v_1.bpmn',
  ]);
  await deployProcess(['ChildProcessInstanceMigration/childProcess_v_2.bpmn']);

  if (
    deployProcessResponse.processes[0] === undefined ||
    deployProcessResponse.processes[1] === undefined
  ) {
    throw new Error('Error deploying process');
  }

  const {
    version: parentVersion,
    processDefinitionKey: parentProcessDefinitionKey,
    bpmnProcessId: parentBpmnProcessId,
  } = deployProcessResponse.processes[0];

  const {bpmnProcessId: childBpmnProcessId} =
    deployProcessResponse.processes[1];

  return {
    processInstances: await createInstances('callActivityParentProcess', 1, 2),
    parentVersion,
    parentProcessDefinitionKey,
    parentBpmnProcessId,
    childBpmnProcessId,
  };
};

export {setup};
