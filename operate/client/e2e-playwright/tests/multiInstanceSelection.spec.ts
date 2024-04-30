/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {config} from '../config';
import {test} from '../test-fixtures';
import {SETUP_WAITING_TIME} from './constants';
import {setup} from './multiInstanceSelection.mocks';

let initialData: Awaited<ReturnType<typeof setup>>;

test.beforeAll(async ({request}) => {
  test.setTimeout(SETUP_WAITING_TIME);
  initialData = await setup();

  const {
    multiInstanceProcessInstance: {processInstanceKey},
  } = initialData;

  // wait until all flow nodes are in incident state
  await expect
    .poll(
      async () => {
        const response = await request.post(
          `${config.endpoint}/v1/flownode-instances/search`,
          {
            data: {
              filter: {
                processInstanceKey,
                flowNodeId: 'taskB',
                type: 'SERVICE_TASK',
                incident: true,
              },
            },
          },
        );

        const flowNodeInstances = await response.json();
        return flowNodeInstances.total;
      },
      {timeout: SETUP_WAITING_TIME},
    )
    .toBe(25);
});

test.beforeEach(async ({processInstancePage}) => {
  const {
    multiInstanceProcessInstance: {processInstanceKey},
  } = initialData;

  await processInstancePage.navigateToProcessInstance({id: processInstanceKey});
});

test.describe('Multi Instance Flow Node Selection', () => {
  test('expand multi instance flow nodes in instance history', async ({
    page,
    processInstancePage,
  }) => {
    const {instanceHistory} = processInstancePage;

    // Expect that the process instance is selected by default
    await expect(
      instanceHistory.getByRole('treeitem', {selected: true}),
    ).toHaveAttribute('aria-label', 'multiInstanceProcess');

    // Unfold 2x Task B (Multi Instance)
    const multiInstanceRow = page.getByRole('treeitem', {
      name: /^task b \(multi instance\)$/i,
    });
    await instanceHistory.locator(multiInstanceRow).first().click();
    await page.keyboard.press('ArrowRight');
    await instanceHistory.locator(multiInstanceRow).first().click();
    await page.keyboard.press('ArrowRight');

    // Expect 10x Task B visible
    await expect(
      instanceHistory.getByRole('treeitem', {name: 'Task B'}),
    ).toHaveCount(2 * 5);
  });

  test('select flow node in diagram', async ({page, processInstancePage}) => {
    const {
      instanceHistory,
      variablePanelEmptyText,
      diagram: {popover},
    } = processInstancePage;

    await processInstancePage.diagram.clickFlowNode('Task B');

    // Check instance history
    await expect(
      instanceHistory.getByRole('treeitem', {
        name: /^task b \(multi instance\)$/i,
        selected: true,
      }),
    ).toHaveCount(5);

    // Check metadata popover
    await expect(popover).toBeVisible();
    await expect(
      popover.getByText(/this flow node triggered 5 times/i),
    ).toBeVisible();
    await expect(popover.getByText(/25 incidents occurred/i)).toBeVisible();

    // Check variable panel
    await expect(variablePanelEmptyText).toBeVisible();

    // Check incident table
    await popover.getByRole('button', {name: /^show incidents$/i}).click();
    await expect(
      page.getByText(/incidents view\s+-\s+25 results/i),
    ).toBeVisible();

    // Check that nothing is selected in incidents table
    await expect(
      page.getByRole('row', {name: /^no more retries left/i}),
    ).toHaveCount(25);
    await expect(
      page.getByRole('row', {name: /^no more retries left/i, selected: true}),
    ).toHaveCount(0);
  });

  test('select single task', async ({page, processInstancePage}) => {
    const {
      instanceHistory,
      variablePanelEmptyText,
      diagram: {popover},
    } = processInstancePage;

    // Select a single Task B
    await instanceHistory
      .locator(
        page.getByRole('treeitem', {
          name: /^task b \(multi instance\)$/i,
        }),
      )
      .first()
      .click();
    await page.keyboard.press('ArrowRight');
    await instanceHistory
      .getByRole('treeitem', {
        name: /^task b$/i,
      })
      .first()
      .click();

    // check metadata popover
    await expect(popover.getByText(/flow node instance key/i)).toBeVisible();
    await expect(
      popover.getByRole('button', {name: /^show more metadata$/i}),
    ).toBeVisible();

    // Check that one instance is selected in incidents table
    await popover.getByRole('button', {name: /^show incident$/i}).click();
    await expect(
      page.getByRole('row', {name: /^no more retries left/i}),
    ).toHaveCount(25);
    await expect(
      page.getByRole('row', {name: /^no more retries left/i, selected: true}),
    ).toHaveCount(1);

    // Check that one instance is selected in instance history
    await expect(
      instanceHistory.getByRole('treeitem', {
        name: /^task b$/i,
        selected: true,
      }),
    ).toHaveCount(1);

    // Check variable panel
    await expect(variablePanelEmptyText).not.toBeVisible();
  });
});
