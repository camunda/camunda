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
    './e2e/tests/resources/processWithAnIncident.bpmn',
    './e2e/tests/resources/processWithMultiIncidents.bpmn',
    './e2e/tests/resources/withoutIncidentsProcess_v_1.bpmn',
    './e2e/tests/resources/processWithMultipleTokens.bpmn',
  ]);

  const instances = await createInstances('processWithAnIncident', 1, 2);
  const instanceWithoutAnIncident = await createSingleInstance(
    'withoutIncidentsProcess',
    1
  );
  const processWithMultipleTokens = await createSingleInstance(
    'processWithMultipleTokens',
    1
  );
  const instanceWithIncidentForIncidentsBar = await createSingleInstance(
    'processWithMultiIncidents',
    1,
    {goUp: 3}
  );

  const instanceWithIncidentToResolve = await createSingleInstance(
    'processWithMultiIncidents',
    1,
    {goUp: 3}
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
    instanceWithIncidentToResolve,
    instanceWithIncidentForIncidentsBar,
    instanceWithoutAnIncident,
    processWithMultipleTokens,
  };
}
