/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {zeebeGrpcApi} from '../api/zeebe-grpc';

const {deployProcesses, createInstances} = zeebeGrpcApi;

const setup = async () => {
  const {deployments} = await deployProcesses([
    'ChildProcessInstanceMigration/callActivityParentProcess.bpmn',
    'ChildProcessInstanceMigration/childProcess_v_1.bpmn',
  ]);

  await deployProcesses([
    'ChildProcessInstanceMigration/childProcess_v_2.bpmn',
  ]);

  const [deployment0, deployment1] = deployments;
  if (
    deployment0?.process === undefined ||
    deployment1?.process === undefined
  ) {
    throw new Error('Error deploying process');
  }

  const {
    version: parentVersion,
    processDefinitionKey: parentProcessDefinitionKey,
    bpmnProcessId: parentBpmnProcessId,
  } = deployment0.process;

  const {bpmnProcessId: childBpmnProcessId} = deployment1.process;

  return {
    processInstances: await createInstances('callActivityParentProcess', 1, 2),
    parentVersion,
    parentProcessDefinitionKey,
    parentBpmnProcessId,
    childBpmnProcessId,
  };
};

export {setup};
