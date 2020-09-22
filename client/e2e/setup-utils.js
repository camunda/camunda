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

async function createSingleInstance(bpmnProcessId, version, variables) {
  const result = await zbc.createWorkflowInstance({
    bpmnProcessId,
    version,
    variables,
  });

  return result.workflowInstanceKey;
}

function completeTask(taskType, shouldFail, variables) {
  zbc.createWorker({
    taskType,
    taskHandler: (job, complete) => {
      if (shouldFail) {
        complete.failure('task failed');
      } else {
        complete.success(variables);
      }
    },
  });
}

export {deploy, createInstances, completeTask, createSingleInstance};
