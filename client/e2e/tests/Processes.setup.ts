/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {deploy, createSingleInstance} from '../setup-utils';

const setup = async () => {
  await deploy(['orderProcess_v_1.bpmn']);

  const instanceWithoutAnIncident = await createSingleInstance(
    'orderProcess',
    1
  );

  await deploy(['processWithAnIncident.bpmn']);

  const instanceWithAnIncident = await createSingleInstance(
    'processWithAnIncident',
    1
  );

  return {instanceWithoutAnIncident, instanceWithAnIncident};
};

export {setup};
