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
  const {deployments: deploymentsV1} = await deployProcesses([
    'ProcessInstanceMigration/orderProcessMigration_v_1.bpmn',
  ]);

  if (deploymentsV1[0] === undefined) {
    throw new Error('Error deploying process');
  }

  const {deployments: deploymentsV2} = await deployProcesses([
    'ProcessInstanceMigration/orderProcessMigration_v_2.bpmn',
  ]);
  if (deploymentsV2[0] === undefined) {
    throw new Error('Error deploying process');
  }

  const {deployments: deploymentsV3} = await deployProcesses([
    'ProcessInstanceMigration/orderProcessMigration_v_3.bpmn',
  ]);
  if (deploymentsV3[0] === undefined) {
    throw new Error('Error deploying process');
  }

  return {
    processV1Instances: await createInstances(
      'orderProcessMigration',
      deploymentsV1[0].process.version,
      10,
    ),
    processV1: deploymentsV1[0].process,
    processV2: deploymentsV2[0].process,
    processV3: deploymentsV3[0].process,
  };
};

export {setup};
