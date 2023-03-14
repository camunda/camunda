/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {deploy, createInstances} from './utils/zeebeUtilities';
export async function setup() {
  await Promise.all([
    deploy('./e2e/tests/resources/usertask_to_be_claimed.bpmn'),
    deploy('./e2e/tests/resources/usertask_for_scrolling_1.bpmn'),
    deploy('./e2e/tests/resources/usertask_for_scrolling_2.bpmn'),
    deploy('./e2e/tests/resources/usertask_for_scrolling_3.bpmn'),
  ]);

  await createInstances('usertask_for_scrolling_3', 1, 1);
  await createInstances('usertask_for_scrolling_2', 1, 50);
  await createInstances('usertask_for_scrolling_2', 1, 50);
  await createInstances('usertask_for_scrolling_2', 1, 50);
  await createInstances('usertask_for_scrolling_2', 1, 50);
  await createInstances('usertask_for_scrolling_1', 1, 1);
  await createInstances('usertask_to_be_claimed', 1, 1); // this task will be seen on top since it is created last
}
