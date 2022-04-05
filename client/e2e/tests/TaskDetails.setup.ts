/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {deploy, createInstances} from './utils/zeebeUtilities';
export async function setup() {
  await deploy([
    './e2e/tests/resources/usertask_to_be_completed.bpmn',
    './e2e/tests/resources/user_task_with_form.bpmn',
    './e2e/tests/resources/user_task_with_form_and_vars.bpmn',
    './e2e/tests/resources/user_task_with_form_rerender_1.bpmn',
    './e2e/tests/resources/user_task_with_form_rerender_2.bpmn',
  ]);

  await Promise.all([
    createInstances('usertask_to_be_completed', 1, 1),
    createInstances('user_registration', 1, 2),
    createInstances('user_registration_with_vars', 1, 1, {
      name: 'Jane',
      age: '50',
    }),
    createInstances('user_task_with_form_rerender_1', 1, 1, {
      name: 'Mary',
      age: '20',
    }),
    createInstances('user_task_with_form_rerender_2', 1, 1, {
      name: 'Stuart',
      age: '30',
    }),
  ]);
}
