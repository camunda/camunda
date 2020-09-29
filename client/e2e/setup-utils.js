/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

const ZB = require('zeebe-node');
const zbc = new ZB.ZBClient({
  onReady: () => console.log('zeebe-node connected!'),
  onConnectionError: () => console.log('zeebe-node disconnected!'),
}); // localhost:26500 || ZEEBE_GATEWAY_ADDRESS

function deploy(processNames) {
  return zbc.deployWorkflow(processNames);
}

function createInstances(bpmnProcessId, version, numberOfInstances) {
  return Promise.all(
    [...new Array(numberOfInstances)].map(() =>
      zbc.createWorkflowInstance({bpmnProcessId, version})
    )
  );
}

function createSingleInstance(processId, version, variables) {
  return zbc.createWorkflowInstance({
    bpmnProcessId: processId,
    version,
    variables,
  });
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
