/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {
  deploy,
  createInstances,
  createSingleInstance,
  completeTask,
} from '../setup-utils';
import {wait} from './utils/wait';

export async function setup() {
  await deploy([
    './tests/resources/processWithAnIncident.bpmn',
    './tests/resources/withoutIncidentsProcess_v_1.bpmn',
    './tests/resources/processWithMultipleTokens.bpmn',
  ]);

  const instances = await createInstances('processWithAnIncident', 1, 3);
  const instanceWithoutAnIncident = await createSingleInstance(
    'withoutIncidentsProcess',
    1
  );
  const processWithMultipleTokens = await createSingleInstance(
    'processWithMultipleTokens',
    1
  );
  completeTask('processWithMultipleTokensTaskA', false, {
    shouldContinue: true,
  });
  completeTask('processWithMultipleTokensTaskB', false);
  await wait(3000);
  completeTask('processWithMultipleTokensTaskA', false, {
    shouldContinue: false,
  });

  return {
    instanceWithIncident: instances[0],
    instanceWithIncidentToCancel: instances[1],
    instanceWithIncidentToResolve: instances[2],
    instanceWithoutAnIncident,
    processWithMultipleTokens,
  };
}
