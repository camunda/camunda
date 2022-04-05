/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {deploy, createInstances} from '../setup-utils';

const setup = async () => {
  await deploy([
    'withoutIncidentsProcess_v_1.bpmn',
    'processWithAnIncident.bpmn',
    'orderProcess_v_1.bpmn',
  ]);

  await createInstances('withoutIncidentsProcess', 1, 10);
  await createInstances('orderProcess', 1, 10);
  await createInstances('processWithAnIncident', 1, 5);
};

export {setup};
