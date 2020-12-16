/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {ZBClient, IWorkflowVariables} from 'zeebe-node';
const zbc = new ZBClient({
  onReady: () => console.log(`Connected!`),
  onConnectionError: () => console.log(`Disconnected!`),
}); // localhost:26500 || ZEEBE_GATEWAY_ADDRESS

const deploy: typeof zbc.deployWorkflow = (processNames) => {
  return zbc.deployWorkflow(processNames);
};

const createInstances = <Variables = IWorkflowVariables>(
  bpmnProcessId: string,
  version: number,
  numberOfInstances: number,
  variables?: Variables,
) => {
  return Promise.all(
    [...new Array(numberOfInstances)].map(() =>
      zbc.createWorkflowInstance<typeof variables>({
        bpmnProcessId,
        version,
        variables,
      }),
    ),
  );
};

export {deploy, createInstances};
