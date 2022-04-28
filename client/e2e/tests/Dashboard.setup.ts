/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {deploy, createInstances, createSingleInstance} from '../setup-utils';

const setup = async () => {
  await deploy([
    'withoutInstancesProcess_v_1.bpmn',
    'withoutIncidentsProcess_v_1.bpmn',
    'onlyIncidentsProcess_v_1.bpmn',
    'orderProcess_v_1.bpmn',
    'processWithAnIncident.bpmn',
  ]);

  await deploy([
    'withoutInstancesProcess_v_2.bpmn',
    'withoutIncidentsProcess_v_2.bpmn',
    'onlyIncidentsProcess_v_2.bpmn',
  ]);

  await Promise.all([
    createInstances('withoutIncidentsProcess', 1, 4),
    createInstances('withoutIncidentsProcess', 2, 8),
    createInstances('onlyIncidentsProcess', 1, 10),
    createInstances('onlyIncidentsProcess', 2, 5),
    createInstances('orderProcess', 1, 10),
    createSingleInstance('processWithAnIncident', 1),
  ]);
};

export {setup};
