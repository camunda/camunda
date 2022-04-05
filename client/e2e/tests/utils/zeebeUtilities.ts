/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {ZBClient, IProcessVariables} from 'zeebe-node';
const zbc = new ZBClient({
  onReady: () => console.log(`Connected!`),
  onConnectionError: () => console.log(`Disconnected!`),
}); // localhost:26500 || ZEEBE_GATEWAY_ADDRESS

const deploy: typeof zbc.deployProcess = (processNames) => {
  return zbc.deployProcess(processNames);
};

const createInstances = <Variables = IProcessVariables>(
  bpmnProcessId: string,
  version: number,
  numberOfInstances: number,
  variables?: Variables,
) => {
  return Promise.all(
    [...new Array(numberOfInstances)].map(() =>
      zbc.createProcessInstance<typeof variables>({
        bpmnProcessId,
        version,
        variables,
      }),
    ),
  );
};

export {deploy, createInstances};
