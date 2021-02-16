/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {ZBClient, IWorkflowVariables} from 'zeebe-node';
const zbc = new ZBClient({
  onReady: () => console.log('zeebe-node connected!'),
  onConnectionError: () => console.log('zeebe-node disconnected!'),
}); // localhost:26500 || ZEEBE_GATEWAY_ADDRESS

function deploy(processNames: string[]) {
  return zbc.deployWorkflow(processNames);
}

function createInstances<Variables = IWorkflowVariables>(
  bpmnProcessId: string,
  version: number,
  numberOfInstances: number,
  variables?: Variables
) {
  return Promise.all(
    [...new Array(numberOfInstances)].map(() =>
      zbc.createWorkflowInstance<typeof variables>({
        bpmnProcessId,
        version,
        variables,
      })
    )
  );
}

function createSingleInstance<Variables = IWorkflowVariables>(
  bpmnProcessId: string,
  version: number,
  variables?: Variables
) {
  return zbc.createWorkflowInstance<typeof variables>({
    bpmnProcessId,
    version,
    variables,
  });
}

function completeTask(
  taskType: string,
  shouldFail: boolean,
  variables?: IWorkflowVariables
) {
  zbc.createWorker({
    taskType,
    taskHandler: (_, complete) => {
      if (shouldFail) {
        complete.failure('task failed');
      } else {
        complete.success(variables);
      }
    },
  });
}

export {deploy, createInstances, completeTask, createSingleInstance};
