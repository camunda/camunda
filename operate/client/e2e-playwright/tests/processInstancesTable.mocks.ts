/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {zeebeGrpcApi} from '../api/zeebe-grpc';
import {wait} from '../utils/wait';

const {deployProcesses, createInstances, createSingleInstance} = zeebeGrpcApi;

// time difference between start dates in ms for sorting test
const startDateDifference = 1000;

async function setup() {
  await deployProcesses([
    'instancesTableProcessA.bpmn',
    'instancesTableProcessB_v_1.bpmn',
    'instancesTableProcessForInfiniteScroll.bpmn',
  ]);
  await deployProcesses(['instancesTableProcessB_v_2.bpmn']);

  const processA = await createInstances('instancesTableProcessA', 1, 30);

  await wait(startDateDifference);

  const processB_v_1 = [
    await createSingleInstance('instancesTableProcessB', 1),
  ];

  await wait(startDateDifference);

  const processB_v_2 = [
    await createSingleInstance('instancesTableProcessB', 2),
  ];

  const instancesForInfiniteScroll = await createInstances(
    'instancesTableProcessForInfiniteScroll',
    1,
    300,
  );

  return {
    instances: {
      processA,
      processB_v_1,
      processB_v_2,
      instancesForInfiniteScroll,
    },
  };
}

export {setup};
