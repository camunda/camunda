/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {deployProcess, createSingleInstance} from '../setup-utils';

export async function setup() {
  await deployProcess(['withoutIncidentsProcess_v_1.bpmn']);

  const instanceWithoutAnIncident = await createSingleInstance(
    'withoutIncidentsProcess',
    1,
    {
      test: 123,
      foo: 'bar',
    }
  );

  return {
    instanceWithoutAnIncident,
  };
}
