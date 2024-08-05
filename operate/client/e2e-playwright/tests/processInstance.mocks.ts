/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {zeebeGrpcApi} from '../api/zeebe-grpc';

const {deployProcesses, createSingleInstance} = zeebeGrpcApi;

export async function setup() {
  await deployProcesses([
    'processWithAnIncident.bpmn',
    'processWithMultiIncidents.bpmn',
    'collapsedSubprocess.bpmn',
    'executionCountProcess.bpmn',
  ]);

  const instanceWithIncidentToCancel = await createSingleInstance(
    'processWithAnIncident',
    1,
  );

  const instanceWithIncidentToResolve = await createSingleInstance(
    'processWithMultiIncidents',
    1,
    {goUp: 3},
  );

  const collapsedSubProcessInstance = await createSingleInstance(
    'collapsedSubProcess',
    1,
  );

  const executionCountProcessInstance = await createSingleInstance(
    'executionCountProcess',
    1,
  );

  return {
    instanceWithIncidentToCancel,
    instanceWithIncidentToResolve,
    collapsedSubProcessInstance,
    executionCountProcessInstance,
  };
}
