/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const ZB = require('zeebe-node');
const zbc = new ZB.ZBClient({
  onReady: () => console.log(`Connected!`),
  onConnectionError: () => console.log(`Disconnected!`),
}); // localhost:26500 || ZEEBE_GATEWAY_ADDRESS

export async function deploy(processNames) {
  const response = await zbc.deployWorkflow(processNames);
  console.log(response);
  return;
}

export async function createInstances(processId, version, noOfInstances) {
  for (var i = 0; i < noOfInstances; i++) {
    const result = await zbc.createWorkflowInstance({
      bpmnProcessId: processId,
      version: version,
    });
    console.log(result);
  }
  return;
}

export async function completeTask(taskType) {
  zbc.createWorker(null, taskType, handler);
  return;
}

function handler(job, complete) {
  console.log('Task variables', job.variables);

  // Task worker business logic goes here
  const updateToBrokerVariables = {
    updatedProperty: 'newValue',
  };

  complete(updateToBrokerVariables);
}
