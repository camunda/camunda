/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

import {screen, within} from '@testing-library/testcafe';
import {config} from '../config';
import {setup} from './InstanceHistory.setup';
import {demoUser} from './utils/Roles';
import {wait} from './utils/wait';
import {getFlowNodeInstances} from './api';

fixture('Instance History')
  .page(config.endpoint)
  .before(async (ctx) => {
    ctx.initialData = await setup();
    await wait();
  })
  .beforeEach(async (t) => {
    await t.useRole(demoUser);
  });

const getNthTreeNodeTestId = (n: number) =>
  screen
    .queryAllByTestId(/^tree-node-/)
    .nth(n)
    .getAttribute('data-testid');

test('Scrolling behavior - root level', async (t) => {
  const {
    initialData: {
      manyFlowNodeInstancesProcessInstance: {processInstanceKey},
    },
  } = t.fixtureCtx;

  await t.navigateTo(`/processes/${processInstanceKey}`);

  await t
    .expect(screen.queryByTestId(`node-details-${processInstanceKey}`).exists)
    .ok()
    .expect(
      within(
        screen.queryByTestId(`node-details-${processInstanceKey}`)
      ).queryByTestId('COMPLETED-icon').exists
    )
    .ok();

  const flowNodeInstances = await getFlowNodeInstances({
    processInstanceId: processInstanceKey,
  });

  const flowNodeInstanceIds = flowNodeInstances[
    processInstanceKey
  ].children.map((instance: {id: string}) => instance.id);

  await t.expect(screen.queryAllByTestId(/^tree-node-/).count).eql(51);

  await t
    .scrollIntoView(
      screen.queryByTestId(`tree-node-${flowNodeInstanceIds[49]}`)
    )
    .expect(getNthTreeNodeTestId(1))
    .eql(`tree-node-${flowNodeInstanceIds[0]}`)
    .expect(screen.queryAllByTestId(/^tree-node-/).count)
    .eql(101);

  await t
    .scrollIntoView(
      screen.queryByTestId(`tree-node-${flowNodeInstanceIds[99]}`)
    )
    .expect(getNthTreeNodeTestId(1))
    .eql(`tree-node-${flowNodeInstanceIds[0]}`)
    .expect(screen.queryAllByTestId(/^tree-node-/).count)
    .eql(151);

  await t
    .scrollIntoView(
      screen.queryByTestId(`tree-node-${flowNodeInstanceIds[149]}`)
    )
    .expect(getNthTreeNodeTestId(1))
    .eql(`tree-node-${flowNodeInstanceIds[0]}`)
    .expect(screen.queryAllByTestId(/^tree-node-/).count)
    .eql(201);

  await t
    .scrollIntoView(
      screen.queryByTestId(`tree-node-${flowNodeInstanceIds[199]}`)
    )
    .expect(getNthTreeNodeTestId(1))
    .eql(`tree-node-${flowNodeInstanceIds[50]}`)
    .expect(screen.queryAllByTestId(/^tree-node-/).count)
    .eql(201);

  await t
    .scroll(screen.getByTestId('instance-history'), 0, 0)
    .expect(getNthTreeNodeTestId(1))
    .eql(`tree-node-${flowNodeInstanceIds[0]}`)
    .expect(screen.queryAllByTestId(/^tree-node-/).count)
    .eql(201);
});

test('Scrolling behaviour - tree level', async (t) => {
  const {
    initialData: {
      bigProcessInstance: {processInstanceKey},
    },
  } = t.fixtureCtx;

  await t.navigateTo(`/processes/${processInstanceKey}`);

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
