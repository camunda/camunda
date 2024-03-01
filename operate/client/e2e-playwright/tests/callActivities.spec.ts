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

import {setup} from './callActivities.mocks';
import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {config} from '../config';
import {SETUP_WAITING_TIME} from './constants';

let initialData: Awaited<ReturnType<typeof setup>>;

test.beforeAll(async ({request}) => {
  test.setTimeout(SETUP_WAITING_TIME);

  initialData = await setup();
  await expect
    .poll(
      async () => {
        const response = await request.get(
          `${config.endpoint}/v1/process-instances/${initialData.callActivityProcessInstance.processInstanceKey}`,
        );

        return response.status();
      },
      {timeout: SETUP_WAITING_TIME},
    )
    .toBe(200);
});

test.describe('Call Activities', () => {
  test('Navigate to called and parent process instances', async ({
    processInstancePage,
    page,
  }) => {
    const processInstanceKey =
      initialData.callActivityProcessInstance.processInstanceKey;

    const {instanceHeader, diagram, instanceHistory} = processInstancePage;

    processInstancePage.navigateToProcessInstance({id: processInstanceKey});

    await expect(page.getByTestId('instance-header-skeleton')).toBeHidden();

    await expect(instanceHeader).toBeVisible();
    await expect(instanceHeader.getByText(processInstanceKey)).toBeVisible();

    await expect(
      instanceHeader.getByText('Call Activity Process'),
    ).toBeVisible();

    await page.getByRole('link', {name: /view all called instances/i}).click();

    const instancesList = page.getByTestId('data-list');

    await expect(instancesList.getByRole('row')).toHaveCount(1);
    await expect(instancesList.getByText('Called Process')).toBeVisible();

    const calledProcessInstanceId = await instancesList
      .getByRole('row')
      .nth(0)
      .getByRole('cell')
      .nth(2)
      .innerText();

    // Navigate to call activity instance
    await instancesList
      .getByRole('link', {
        name: /view parent instance/i,
      })
      .click();

    // Expect correct header
    await expect(instanceHeader.getByText(processInstanceKey)).toBeVisible();

    await expect(
      instanceHeader.getByText('Call Activity Process'),
    ).toBeVisible();

    // Expect correct instance history
    await expect(
      instanceHistory.getByText('Call Activity Process'),
    ).toBeVisible();
    await expect(instanceHistory.getByText('StartEvent_1')).toBeVisible();
    await expect(
      instanceHistory.getByText('Call Activity', {
        exact: true,
      }),
    ).toBeVisible();
    await expect(instanceHistory.getByText('Event_1p0nsc7')).toBeVisible();

    // Expect correct diagram

    await expect(diagram.getFlowNode('call activity')).toBeVisible();

    // Navigate to called process instance
    await diagram.clickFlowNode('call Activity');

    const popover = page.getByTestId('popover');

    await expect(popover.getByText(/Called Process Instance/)).toBeVisible();

    await popover
      .getByRole('link', {
        name: /view called process instance/i,
      })
      .click();

    // Expect correct header
    await expect(
      instanceHeader.getByText(calledProcessInstanceId),
    ).toBeVisible();
    await expect(
      instanceHeader.getByText('Called Process', {
        exact: true,
      }),
    ).toBeVisible();

    // Expect correct instance history
    await expect(
      instanceHistory.getByText('Called Process', {
        exact: true,
      }),
    ).toBeVisible();
    await expect(instanceHistory.getByText('Process started')).toBeVisible();
    await expect(instanceHistory.getByText('Event_0y6k56d')).toBeVisible();

    // Expect correct diagram
    await expect(diagram.getFlowNode('Process started')).toBeVisible();

    // Navigate to parent instance
    await instanceHeader
      .getByRole('link', {
        name: /view parent instance/i,
      })
      .click();

    // Expect correct header
    await expect(instanceHeader.getByText(processInstanceKey)).toBeVisible();

    await expect(
      instanceHeader.getByText('Call Activity Process'),
    ).toBeVisible();

    // Expect correct instance history
    await expect(
      instanceHistory.getByText('Call Activity Process'),
    ).toBeVisible();
    await expect(instanceHistory.getByText('StartEvent_1')).toBeVisible();
    await expect(
      instanceHistory.getByText('Call Activity', {
        exact: true,
      }),
    ).toBeVisible();
    await expect(instanceHistory.getByText('Event_1p0nsc7')).toBeVisible();

    await expect(diagram.getFlowNode('Call Activity')).toBeVisible();
  });
});
