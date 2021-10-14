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

test('Scrolling behavior - root level', async (t) => {
  const {
    initialData: {manyFlowNodeInstancesProcessInstance},
  } = t.fixtureCtx;

  await t.navigateTo(
    `/instances/${manyFlowNodeInstancesProcessInstance.processInstanceKey}`
  );

  await t
    .expect(
      screen.queryByTestId(
        `node-details-${manyFlowNodeInstancesProcessInstance.processInstanceKey}`
      ).exists
    )
    .ok()
    .expect(
      within(
        screen.queryByTestId(
          `node-details-${manyFlowNodeInstancesProcessInstance.processInstanceKey}`
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

test('Scrolling behaviour - tree level', async (t) => {
  const {
    initialData: {bigProcessInstance},
  } = t.fixtureCtx;

  await t.navigateTo(`/instances/${bigProcessInstance.processInstanceKey}`);

  const withinFirstSubtree = within(
    screen.queryAllByTestId(/^tree-node-/).nth(4)
  );

  await t.click(
    withinFirstSubtree.queryByRole('button', {
      name: 'Unfold Task B (Multi Instance)',
    })
  );

  /**
   * Scrolling down
   */
  await t
    .scrollIntoView(withinFirstSubtree.queryAllByTestId(/^tree-node-/).nth(49))
    .expect(withinFirstSubtree.queryAllByTestId(/^tree-node-/).count)
    .eql(100);

  await t
    .scrollIntoView(withinFirstSubtree.queryAllByTestId(/^tree-node-/).nth(99))
    .expect(withinFirstSubtree.queryAllByTestId(/^tree-node-/).count)
    .eql(150);

  await t
    .scrollIntoView(withinFirstSubtree.queryAllByTestId(/^tree-node-/).nth(149))
    .expect(withinFirstSubtree.queryAllByTestId(/^tree-node-/).count)
    .eql(200);

  let firstItemId = await withinFirstSubtree
    .queryAllByTestId(/^tree-node-/)
    .nth(0)
    .getAttribute('data-testid');
  await t.expect(firstItemId).notEql(null);

  let lastItemId = await withinFirstSubtree
    .queryAllByTestId(/^tree-node-/)
    .nth(199)
    .getAttribute('data-testid');
  await t.expect(lastItemId).notEql(null);

  await t
    .scrollIntoView(withinFirstSubtree.queryAllByTestId(/^tree-node-/).nth(199))
    // @ts-expect-error: firstItemId won't be null here
    .expect(screen.queryByTestId(firstItemId).exists)
    .notOk()
    .expect(
      withinFirstSubtree
        .queryAllByTestId(/^tree-node-/)
        .nth(149)
        .getAttribute('data-testid')
    )
    .eql(lastItemId)
    .expect(withinFirstSubtree.queryAllByTestId(/^tree-node-/).count)
    .eql(200);

  /**
   * Scrolling up
   */
  firstItemId = await withinFirstSubtree
    .queryAllByTestId(/^tree-node-/)
    .nth(0)
    .getAttribute('data-testid');
  await t.expect(firstItemId).notEql(null);

  lastItemId = await withinFirstSubtree
    .queryAllByTestId(/^tree-node-/)
    .nth(199)
    .getAttribute('data-testid');
  await t.expect(lastItemId).notEql(null);

  await t
    .scrollIntoView(withinFirstSubtree.queryAllByTestId(/^tree-node-/).nth(0))
    // @ts-expect-error: lastItemId won't be null here
    .expect(screen.queryByTestId(lastItemId).exists)
    .notOk()
    .expect(
      withinFirstSubtree
        .queryAllByTestId(/^tree-node-/)
        .nth(50)
        .getAttribute('data-testid')
    )
    .eql(firstItemId)
    .expect(withinFirstSubtree.queryAllByTestId(/^tree-node-/).count)
    .eql(200);
});
