/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {deploy, createSingleInstance} from '../setup-utils';

export async function setup() {
  await deploy(['callActivityProcess.bpmn', 'calledProcess.bpmn']);

  return {
    callActivityProcessInstance: await createSingleInstance(
      'CallActivityProcess',
      1
    ),
  };
}
