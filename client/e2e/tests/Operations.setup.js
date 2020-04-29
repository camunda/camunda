/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {deploy, createInstances} from '../setup-utils';
import {createOperation} from './api';

async function setup() {
  await deploy(['./tests/resources/genericProcess.bpmn']);

  const instances = await createInstances('genericProcess', 1, 2);
  const [, instance] = instances;

  await sleep(10000);

  const operations = [
    await createOperation({
      id: instance.workflowInstanceKey,
      operationType: 'CANCEL_WORKFLOW_INSTANCE',
    }),
  ];

  return {instances, operations};
}

async function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export {setup};
