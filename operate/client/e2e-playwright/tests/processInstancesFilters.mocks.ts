/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {zeebeGrpcApi} from '../api/zeebe-grpc';

const {deployProcesses, createInstances, createSingleInstance} = zeebeGrpcApi;

const setup = async () => {
  await deployProcesses(['Filters/processWithMultipleVersions_v_1.bpmn']);
  await deployProcesses(['Filters/processWithMultipleVersions_v_2.bpmn']);
  await deployProcesses(['Filters/processWithAnError.bpmn']);

  await createInstances('processWithMultipleVersions', 1, 1);
  await createInstances('processWithMultipleVersions', 2, 1);
  await createInstances('processWithAnError', 1, 1);

  await deployProcesses(['orderProcess_v_1.bpmn']);

  const orderProcessInstance = await createSingleInstance('orderProcess', 1, {
    filtersTest: 123,
  });

  await deployProcesses(['callActivityProcess.bpmn', 'calledProcess.bpmn']);

  const callActivityProcessInstance = await createSingleInstance(
    'CallActivityProcess',
    1,
    {filtersTest: 456},
  );

  return {
    orderProcessInstance,
    callActivityProcessInstance,
  };
};

export {setup};
