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
  const deployProcessResponse = await deployProcesses([
    'orderProcess_v_1.bpmn',
  ]);

  if (deployProcessResponse.deployments[0]?.process === undefined) {
    throw new Error('Error deploying process');
  }

  const {version, processDefinitionKey, bpmnProcessId} =
    deployProcessResponse.deployments[0].process;

  return {
    processInstances: await createInstances('orderProcess', version, 10),
    processDefinitionKey,
    bpmnProcessId,
    version,
  };
};

export {setup};
