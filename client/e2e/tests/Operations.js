/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {Selector} from 'testcafe';
import {config} from '../config';

import {setup} from './Operations.setup';
import {instancesLink} from './Header.elements';
import {demoUser} from './utils/Roles';

fixture('Operations')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser);
  });

test('Operation creation', async (t) => {
  const {initialData} = t.fixtureCtx;
  const [instance] = initialData.instances;

  await t.navigateTo('/');
  await t
    .click(instancesLink)
    .typeText(Selector('[name="ids"]'), instance.workflowInstanceKey);

  await t
    .click(
      Selector(`[title="Cancel Instance ${instance.workflowInstanceKey}"]`)
    )
    .click(Selector('[title="Expand Operations"]'));

  await t
    .expect(Selector('[data-test="operation-id"]').count)
    .eql(initialData.operations.length + 1);
});
