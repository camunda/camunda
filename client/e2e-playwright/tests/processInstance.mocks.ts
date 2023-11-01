/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {deployProcess, createSingleInstance} from '../setup-utils';

export async function setup() {
  await deployProcess([
    'processWithAnIncident.bpmn',
    'processWithMultiIncidents.bpmn',
    'collapsedSubprocess.bpmn',
  ]);

  const instanceWithIncidentToCancel = await createSingleInstance(
    'processWithAnIncident',
    1,
  );

  const instanceWithIncidentToResolve = await createSingleInstance(
    'processWithMultiIncidents',
    1,
    {goUp: 3},
  );

  const collapsedSubProcessInstance = await createSingleInstance(
    'collapsedSubProcess',
    1,
  );

  return {
    instanceWithIncidentToCancel,
    instanceWithIncidentToResolve,
    collapsedSubProcessInstance,
  };
}
