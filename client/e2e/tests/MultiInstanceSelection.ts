/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {wait} from './utils/wait';
import {demoUser} from './utils/Roles';
import {setup} from './MultiInstanceSelection.setup';

fixture('Multi Instance Flow Node Selection')
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser);
    await t.maximizeWindow();

    const {
      initialData: {multiInstanceProcessInstance},
    } = t.fixtureCtx;

    const processInstanceId = multiInstanceProcessInstance.processInstanceKey;
    await t.navigateTo(`/processes/${processInstanceId}`);
  });
