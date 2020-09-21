/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {deploy, createInstances, createSingleInstance} from '../setup-utils';

export async function setup() {
  await deploy(['./tests/resources/processWithAnIncident.bpmn']);
  await deploy(['./tests/resources/withoutIncidentsProcess_v_1.bpmn']);

  const instances = await createInstances('processWithAnIncident', 1, 3);
  const instanceWithoutAnIncident = await createSingleInstance(
    'withoutIncidentsProcess',
    1
  );

  return {
    instanceWithIncident: instances[0],
    instanceWithIncidentToCancel: instances[1],
    instanceWithIncidentToResolve: instances[2],
    instanceWithoutAnIncident,
  };
}
