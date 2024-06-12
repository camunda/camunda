/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ZBClient, IProcessVariables, JSONDoc} from 'zeebe-node';
const zbc = new ZBClient(); // localhost:26500 || ZEEBE_GATEWAY_ADDRESS

const deploy: typeof zbc.deployProcess = (processNames) => {
  return zbc.deployProcess(processNames);
};

const createInstances = <Variables extends JSONDoc = IProcessVariables>(
  bpmnProcessId: string,
  version: number,
  numberOfInstances: number,
  variables?: Variables,
) => {
  return Promise.all(
    [...new Array(numberOfInstances)].map(() =>
      zbc.createProcessInstance<Variables>({
        bpmnProcessId,
        version,
        variables: variables || ({} as Variables),
      }),
    ),
  );
};

export {deploy, createInstances};
