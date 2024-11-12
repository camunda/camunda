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
  await deployProcesses(['processWithListener.bpmn']);
  const instance = await createSingleInstance('processWithListener', 1);

  return {instance};
};

export {setup};
