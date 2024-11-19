/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {zeebeGrpcApi} from '../api/zeebe-grpc';

const {createSingleInstance, deployProcess, createWorker} = zeebeGrpcApi;

const setup = async () => {
  await deployProcess('processWithListener.bpmn');
  const instance = await createSingleInstance('processWithListener', 1);

  await deployProcess('processWithUserTaskListener.bpmn');
  const userTaskProcessInstance = await createSingleInstance(
    'processWithUserTaskListener',
    1,
  );

  createWorker('completeListener', false);
  createWorker('endListener', false);

  return {instance, userTaskProcessInstance};
};

export {setup};
