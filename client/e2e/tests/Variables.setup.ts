/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {deploy, createInstances} from './utils/zeebeUtilities';
export async function setup() {
  await deploy('./e2e/tests/resources/usertask_with_variables.bpmn');
  await deploy('./e2e/tests/resources/usertask_without_variables.bpmn');
  await createInstances('usertask_without_variables', 1, 1);
  await createInstances('usertask_with_variables', 1, 2, {
    testData: 'something',
  });
}
