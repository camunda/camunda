/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {zeebeGrpcApi} from '../api/zeebe-grpc';

const {deployProcesses, createSingleInstance} = zeebeGrpcApi;

const setup = async () => {
  const deployResponse = await deployProcesses(['orderProcess_v_1.bpmn']);
  const deployedProcess = deployResponse.deployments[0]?.process;

  const instanceWithoutAnIncident = await createSingleInstance(
    'orderProcess',
    deployedProcess!.version,
  );

  await deployProcesses(['processWithAnIncident.bpmn']);

  const instanceWithAnIncident = await createSingleInstance(
    'processWithAnIncident',
    1,
  );

  await deployProcesses(['processToDelete.bpmn']);

  const instanceToCancel = await createSingleInstance('processToDelete', 1);

  return {
    deployedProcess,
    instanceWithoutAnIncident,
    instanceWithAnIncident,
    instanceToCancel,
  };
};

export {setup};
