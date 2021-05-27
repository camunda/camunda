/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {config} from '../config';
import {setup} from './InstanceHistory.setup';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {screen, within} from '@testing-library/testcafe';

fixture('Instance History')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser);
  });

test('Scrolling behavior', async (t) => {
  const {
    initialData: {processInstance},
  } = t.fixtureCtx;

  await t.navigateTo(`/instances/${processInstance.processInstanceKey}`);

  await t
    .expect(
      screen.queryByTestId(`node-details-${processInstance.processInstanceKey}`)
        .exists
    )
    .ok()
    .expect(
      within(
        screen.queryByTestId(
          `node-details-${processInstance.processInstanceKey}`
        )
      ).queryByTestId('COMPLETED-icon').exists
    )
    .ok();

  await t.expect(screen.queryAllByTestId(/^tree-node-/).count).eql(51);

  await t
    .scrollIntoView(screen.queryAllByTestId(/^tree-node-/).nth(50))
    .expect(screen.queryAllByTestId(/^tree-node-/).nth(1).textContent)
    .match(/^StartEvent_1$/)
    .expect(screen.queryAllByTestId(/^tree-node-/).count)
    .eql(101);

  await t
    .scrollIntoView(screen.queryAllByTestId(/^tree-node-/).nth(100))
    .expect(screen.queryAllByTestId(/^tree-node-/).nth(1).textContent)
    .match(/^StartEvent_1$/)
    .expect(screen.queryAllByTestId(/^tree-node-/).count)
    .eql(151);

  await t
    .scrollIntoView(screen.queryAllByTestId(/^tree-node-/).nth(150))
    .expect(screen.queryAllByTestId(/^tree-node-/).nth(1).textContent)
    .match(/^StartEvent_1$/)
    .expect(screen.queryAllByTestId(/^tree-node-/).count)
    .eql(201);

  await t
    .scrollIntoView(screen.queryAllByTestId(/^tree-node-/).nth(200))
    .expect(screen.queryAllByTestId(/^tree-node-/).nth(1).textContent)
    .match(/^Continue\?$/)
    .expect(screen.queryAllByTestId(/^tree-node-/).count)
    .eql(201);

  await t
    .scrollIntoView(screen.queryAllByTestId(/^tree-node-/).nth(1))
    .expect(screen.queryAllByTestId(/^tree-node-/).nth(1).textContent)
    .match(/^StartEvent_1$/)
    .expect(screen.queryAllByTestId(/^tree-node-/).count)
    .eql(201);
});
