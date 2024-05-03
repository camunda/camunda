/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {deployProcess, createSingleInstance} from '../setup-utils';

const setup = async () => {
  await deployProcess(['orderProcess_v_1.bpmn']);

  const instanceWithoutAnIncident = await createSingleInstance(
    'orderProcess',
    1,
  );

  await deployProcess(['processWithAnIncident.bpmn']);

  const instanceWithAnIncident = await createSingleInstance(
    'processWithAnIncident',
    1,
  );

  await deployProcess(['processToDelete.bpmn']);

  const instanceToCancel = await createSingleInstance('processToDelete', 1);

  return {instanceWithoutAnIncident, instanceWithAnIncident, instanceToCancel};
};

export {setup};
