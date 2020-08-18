/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {deploy, createInstances} from './utils/zeebeUtilities';
export async function setup() {
  await deploy('./e2e/tests/resources/usertask_to_be_claimed.bpmn');
  await createInstances('usertask_to_be_claimed', 1, 1);
}
