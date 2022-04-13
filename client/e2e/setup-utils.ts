/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ZBClient, IProcessVariables, ZBWorkerTaskHandler} from 'zeebe-node';
import * as path from 'path';
import {deployDecisions} from './tests/utils/deployDecisions';
import {config} from './config';

const zbc = new ZBClient({
  onReady: () => console.log('zeebe-node connected!'),
  onConnectionError: () => console.log('zeebe-node disconnected!'),
}); // localhost:26500 || ZEEBE_GATEWAY_ADDRESS

deployDecisions();

function deploy(processNames: string[]) {
  const paths = processNames.map((processName) =>
    path.join(config.e2eBasePath, 'tests', 'resources', processName)
  );
  return zbc.deployProcess(paths);
}

function createInstances<Variables = IProcessVariables>(
  bpmnProcessId: string,
  version: number,
  numberOfInstances: number,
  variables?: Variables
) {
  return Promise.all(
    [...new Array(numberOfInstances)].map(() =>
      zbc.createProcessInstance<typeof variables>({
        bpmnProcessId,
        version,
        variables,
      })
    )
  );
}

function createSingleInstance<Variables = IProcessVariables>(
  bpmnProcessId: string,
  version: number,
  variables?: Variables
) {
  return zbc.createProcessInstance<typeof variables>({
    bpmnProcessId,
    version,
    variables,
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
  pollInterval = 300
) {
  zbc.createWorker({
    taskType,
    taskHandler,
    pollInterval,
  });
}

export {deploy, createInstances, completeTask, createSingleInstance};
