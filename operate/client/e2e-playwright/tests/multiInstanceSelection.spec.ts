/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
