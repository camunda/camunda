/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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

import {setup} from './processInstanceHistory.mocks';
import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {config} from '../config';
import {SETUP_WAITING_TIME_LONG} from './constants';
import {ENDPOINTS} from './api/endpoints';

let initialData: Awaited<ReturnType<typeof setup>>;

test.beforeAll(async ({request}) => {
  initialData = await setup();
  test.setTimeout(SETUP_WAITING_TIME_LONG);

  await Promise.all([
    expect
      .poll(
        async () => {
          const response = await request.post(
            `${config.endpoint}/v1/flownode-instances/search`,
            {
              data: {
                filter: {
                  processInstanceKey:
                    initialData.manyFlowNodeInstancesProcessInstance
                      .processInstanceKey,
                },
              },
            },
          );

          const flowNodeInstances = await response.json();
          return flowNodeInstances.total;
        },
        {timeout: SETUP_WAITING_TIME_LONG},
      )
      .toBeGreaterThan(250),
    expect
      .poll(
        async () => {
          const response = await request.post(
            `${config.endpoint}/v1/flownode-instances/search`,
            {
              data: {
                filter: {
                  processInstanceKey:
                    initialData.bigProcessInstance.processInstanceKey,
                  flowNodeId: 'taskB',
                },
              },
            },
          );

          const flowNodeInstances = await response.json();
          return flowNodeInstances.total;
        },
        {timeout: SETUP_WAITING_TIME_LONG},
      )
      .toBe(502),
  ]);
});

test.describe('Process Instance History', () => {
  test('Scrolling behavior - root level', async ({
    page,
    processInstancePage,
  }) => {
    const processInstanceKey =
      initialData.manyFlowNodeInstancesProcessInstance.processInstanceKey;

    await processInstancePage.navigateToProcessInstance({
      id: processInstanceKey,
    });

    await expect(
      page.getByTestId(`node-details-${processInstanceKey}`),
    ).toBeVisible();

    await expect(
      page
        .getByTestId(`node-details-${processInstanceKey}`)
        .getByTestId('COMPLETED-icon'),
    ).toBeVisible();

    const response = await page.request.post(ENDPOINTS.getFlowNodeInstances(), {
      data: {
        queries: [
          {processInstanceId: processInstanceKey, treePath: processInstanceKey},
        ],
      },
    });

    const flowNodeInstances = await response.json();

    const flowNodeInstanceIds = flowNodeInstances[
      processInstanceKey
    ].children.map((instance: {id: string}) => instance.id);

    await expect(page.getByRole('treeitem')).toHaveCount(51);

    await page
      .getByTestId(`tree-node-${flowNodeInstanceIds[49]}`)
      .scrollIntoViewIfNeeded();

    await expect(await processInstancePage.getNthTreeNodeTestId(1)).toBe(
      `tree-node-${flowNodeInstanceIds[0]}`,
    );

    await expect(page.getByRole('treeitem')).toHaveCount(101);

    await page
      .getByTestId(`tree-node-${flowNodeInstanceIds[99]}`)
      .scrollIntoViewIfNeeded();

    await expect(await processInstancePage.getNthTreeNodeTestId(1)).toBe(
      `tree-node-${flowNodeInstanceIds[0]}`,
    );

    await expect(page.getByRole('treeitem')).toHaveCount(151);

    await page
      .getByTestId(`tree-node-${flowNodeInstanceIds[149]}`)
      .scrollIntoViewIfNeeded();

    await expect(await processInstancePage.getNthTreeNodeTestId(1)).toBe(
      `tree-node-${flowNodeInstanceIds[0]}`,
    );
    await expect(page.getByRole('treeitem')).toHaveCount(201);

    await page
      .getByTestId(`tree-node-${flowNodeInstanceIds[199]}`)
      .scrollIntoViewIfNeeded();

    await expect
      .poll(() => processInstancePage.getNthTreeNodeTestId(1))
      .toBe(`tree-node-${flowNodeInstanceIds[50]}`);

    await expect(page.getByRole('treeitem')).toHaveCount(201);

    await page
      .getByTestId(`tree-node-${flowNodeInstanceIds[50]}`)
      .scrollIntoViewIfNeeded();

    await expect
      .poll(() => processInstancePage.getNthTreeNodeTestId(1))
      .toBe(`tree-node-${flowNodeInstanceIds[0]}`);

    await expect(page.getByRole('treeitem')).toHaveCount(201);
  });

  test('Scrolling behaviour - tree level', async ({
    page,
    processInstancePage,
  }) => {
    const processInstanceKey =
      initialData.bigProcessInstance.processInstanceKey;

    await page.addInitScript(() => {
      window.localStorage.setItem(
        'panelStates',
        JSON.stringify({'process-detail-vertical-panel': [75, 25]}),
      );
    });

    await processInstancePage.navigateToProcessInstance({
      id: processInstanceKey,
    });

    const firstSubtree = page.getByRole('treeitem').nth(4);

    await firstSubtree.press('ArrowRight');

    /**
     * Scrolling down
     */
    await firstSubtree.getByRole('treeitem').nth(49).scrollIntoViewIfNeeded();
    await expect(firstSubtree.getByRole('treeitem').nth(47)).toBeInViewport();
    await expect(firstSubtree.getByRole('treeitem').nth(48)).toBeInViewport();
    await expect(firstSubtree.getByRole('treeitem').nth(49)).toBeInViewport();
    await expect(firstSubtree.getByRole('treeitem').nth(50)).toBeInViewport();
    await expect(firstSubtree.getByRole('treeitem').nth(51)).toBeInViewport();

    await expect(firstSubtree.getByRole('treeitem')).toHaveCount(100);

    await firstSubtree.getByRole('treeitem').nth(99).scrollIntoViewIfNeeded();
    await expect(firstSubtree.getByRole('treeitem')).toHaveCount(150);
    await expect(firstSubtree.getByRole('treeitem').nth(97)).toBeInViewport();
    await expect(firstSubtree.getByRole('treeitem').nth(98)).toBeInViewport();
    await expect(firstSubtree.getByRole('treeitem').nth(99)).toBeInViewport();
    await expect(firstSubtree.getByRole('treeitem').nth(100)).toBeInViewport();
    await expect(firstSubtree.getByRole('treeitem').nth(101)).toBeInViewport();

    await firstSubtree.getByRole('treeitem').nth(149).scrollIntoViewIfNeeded();
    await expect(firstSubtree.getByRole('treeitem')).toHaveCount(200);
    await expect(firstSubtree.getByRole('treeitem').nth(120)).toBeInViewport();
    await expect(firstSubtree.getByRole('treeitem').nth(121)).toBeInViewport();
    await expect(firstSubtree.getByRole('treeitem').nth(122)).toBeInViewport();
    await expect(firstSubtree.getByRole('treeitem').nth(123)).toBeInViewport();

    let firstItemId = await firstSubtree
      .getByRole('treeitem')
      .nth(0)
      .getAttribute('data-testid');

    await expect(firstItemId).not.toBe(null);

    let lastItemId = await firstSubtree
      .getByRole('treeitem')
      .nth(199)
      .getAttribute('data-testid');

    await expect(lastItemId).not.toBe(null);

    await firstSubtree.getByRole('treeitem').nth(199).scrollIntoViewIfNeeded();

    // @ts-expect-error: firstItemId won't be null here
    await expect(page.getByTestId(firstItemId)).not.toBeVisible();

    await expect(
      await firstSubtree
        .getByRole('treeitem')
        .nth(149)
        .getAttribute('data-testid'),
    ).toBe(lastItemId);

    await expect(firstSubtree.getByRole('treeitem')).toHaveCount(200);
    await expect(firstSubtree.getByRole('treeitem').nth(120)).toBeInViewport();
    await expect(firstSubtree.getByRole('treeitem').nth(121)).toBeInViewport();
    await expect(firstSubtree.getByRole('treeitem').nth(122)).toBeInViewport();
    await expect(firstSubtree.getByRole('treeitem').nth(123)).toBeInViewport();

    firstItemId = await firstSubtree
      .getByRole('treeitem')
      .nth(0)
      .getAttribute('data-testid');
    await expect(firstItemId).not.toBe(null);

    lastItemId = await firstSubtree
      .getByRole('treeitem')
      .nth(199)
      .getAttribute('data-testid');
    await expect(lastItemId).not.toBe(null);

    /**
     * Scrolling up
     */
    await firstSubtree.getByRole('treeitem').nth(0).scrollIntoViewIfNeeded();

    // @ts-expect-error: lastItemId won't be null here
    await expect(page.getByTestId(lastItemId)).not.toBeVisible();

    await expect(
      await firstSubtree
        .getByRole('treeitem')
        .nth(50)
        .getAttribute('data-testid'),
    ).toBe(firstItemId);

    await expect(firstSubtree.getByRole('treeitem')).toHaveCount(200);
    await expect(firstSubtree.getByRole('treeitem').nth(49)).toBeInViewport();
    await expect(firstSubtree.getByRole('treeitem').nth(50)).toBeInViewport();
    await expect(firstSubtree.getByRole('treeitem').nth(51)).toBeInViewport();
    await expect(firstSubtree.getByRole('treeitem').nth(52)).toBeInViewport();
  });
});
