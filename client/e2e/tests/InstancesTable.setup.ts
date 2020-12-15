/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {deploy, createInstances, createSingleInstance} from '../setup-utils';
import {wait} from './utils/wait';

// time difference between start dates in ms for sorting test
const startDateDifference = 1000;

async function setup() {
  await deploy([
    './e2e/tests/resources/instancesTableProcessA.bpmn',
    './e2e/tests/resources/instancesTableProcessB_v_1.bpmn',
    './e2e/tests/resources/instancesTableProcessForInfiniteScroll.bpmn',
  ]);
  await deploy(['./e2e/tests/resources/instancesTableProcessB_v_2.bpmn']);

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
    300
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
