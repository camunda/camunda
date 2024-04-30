/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  ZBClient,
  IProcessVariables,
  ZBWorkerTaskHandler,
  CreateProcessInstanceResponse,
} from 'zeebe-node';
import * as path from 'path';
import {config} from './config';

const zbc = new ZBClient({
  onReady: () => console.log('zeebe-node connected!'),
  onConnectionError: () => console.log('zeebe-node disconnected!'),
}); // localhost:26500 || ZEEBE_GATEWAY_ADDRESS

function getFullFilePath(filename: string) {
  return path.join(config.e2eBasePath, 'tests', 'resources', filename);
}

function deployDecision(filenames: string[]) {
  return Promise.all(
    filenames
      .map(getFullFilePath)
      .map((decisionFilename) => zbc.deployResource({decisionFilename})),
  );
}

function deployProcess(filenames: string[]) {
  return zbc.deployProcess(filenames.map(getFullFilePath));
}

async function createInstances<Variables = IProcessVariables>(
  bpmnProcessId: string,
  version: number,
  numberOfInstances: number,
  variables?: Variables,
): Promise<CreateProcessInstanceResponse[]> {
  const batchSize = Math.min(numberOfInstances, 50);

  const responses = await Promise.all(
    [...new Array(batchSize)].map(() =>
      zbc.createProcessInstance({
        bpmnProcessId,
        version,
        variables: {...variables},
      }),
    ),
  );

  if (batchSize < 50) {
    return responses;
  }

  return [
    ...responses,
    ...(await createInstances(
      bpmnProcessId,
      version,
      numberOfInstances - batchSize,
      variables,
    )),
  ];
}

function createSingleInstance<Variables = IProcessVariables>(
  bpmnProcessId: string,
  version: number,
  variables?: Variables,
) {
  return zbc.createProcessInstance({
    bpmnProcessId,
    version,
    variables: {...variables},
  });
}

function completeTask(
  taskType: string,
  shouldFail: boolean,
  variables?: IProcessVariables,
  taskHandler: ZBWorkerTaskHandler = (job) => {
    if (shouldFail) {
      return job.fail('task failed');
    } else {
      return job.complete(variables);
    }
  },
  pollInterval = 300,
) {
  zbc.createWorker({
    taskType,
    taskHandler,
    pollInterval,
  });
}

export {
  deployProcess,
  createInstances,
  completeTask,
  createSingleInstance,
  deployDecision,
};
