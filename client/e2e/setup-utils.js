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

function deploy(processNames) {
  return zbc.deployWorkflow(processNames);
}

function createInstances(bpmnProcessId, version, numberOfInstances) {
  return Promise.all(
    [...new Array(numberOfInstances)].map(() =>
      zbc.createWorkflowInstance({
        bpmnProcessId,
        version,
      })
    )
  );
}

async function createSingleInstance(processId, version) {
  const result = await zbc.createWorkflowInstance({
    bpmnProcessId: processId,
    version: version,
    variables: {
      testData: 'something',
    },
  });

  return result.workflowInstanceKey;
}

function completeTask(taskType) {
  zbc.createWorker(null, taskType, handler);
}

function handler(job, complete) {
  // Task worker business logic goes here
  const updateToBrokerVariables = {
    updatedProperty: 'newValue',
  };

  complete(updateToBrokerVariables);
}

export {deploy, createInstances, completeTask, createSingleInstance};
