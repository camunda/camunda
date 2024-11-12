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
  await deployProcesses([
    'withoutInstancesProcess_v_1.bpmn',
    'withoutIncidentsProcess_v_1.bpmn',
    'onlyIncidentsProcess_v_1.bpmn',
    'orderProcess_v_1.bpmn',
    'processWithAnIncident.bpmn',
  ]);

  await deployProcesses([
    'withoutInstancesProcess_v_2.bpmn',
    'withoutIncidentsProcess_v_2.bpmn',
    'onlyIncidentsProcess_v_2.bpmn',
  ]);
  const instancesList = await Promise.all([
    createInstances('withoutIncidentsProcess', 1, 4),
    createInstances('withoutIncidentsProcess', 2, 8),
    createInstances('onlyIncidentsProcess', 1, 10),
    createInstances('onlyIncidentsProcess', 2, 5),
    createInstances('orderProcess', 1, 10),
    createSingleInstance('processWithAnIncident', 1),
  ]);

  const allInstances = instancesList.flatMap((instances) => instances);

  return {
    instanceIds: allInstances.map((instance) => instance.processInstanceKey),
    instanceWithAnIncident: allInstances.find(
      ({bpmnProcessId}) => bpmnProcessId === 'processWithAnIncident',
    )?.processInstanceKey,
  };
};

export {setup};
