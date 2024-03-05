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

import {setup} from './processInstance.mocks';
import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {
  DATE_REGEX,
  DEFAULT_TEST_TIMEOUT,
  SETUP_WAITING_TIME,
} from './constants';
import {config} from '../config';

let initialData: Awaited<ReturnType<typeof setup>>;

test.beforeAll(async ({request}) => {
  initialData = await setup();
  test.setTimeout(SETUP_WAITING_TIME);

  await Promise.all([
    expect
      .poll(
        async () => {
          const response = await request.get(
            `${config.endpoint}/v1/process-instances/${initialData.instanceWithIncidentToResolve.processInstanceKey}`,
          );

          return response.status();
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toBe(200),
    expect
      .poll(
        async () => {
          const response = await request.get(
            `${config.endpoint}/v1/process-instances/${initialData.instanceWithIncidentToCancel.processInstanceKey}`,
          );

          return response.status();
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toBe(200),
    expect
      .poll(
        async () => {
          const response = await request.get(
            `${config.endpoint}/v1/process-instances/${initialData.collapsedSubProcessInstance.processInstanceKey}`,
          );

          return response.status();
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toBe(200),
    expect
      .poll(
        async () => {
          const response = await request.post(
            `${config.endpoint}/v1/incidents/search`,
            {
              data: {
                filter: {
                  processInstanceKey: parseInt(
                    initialData.instanceWithIncidentToResolve
                      .processInstanceKey,
                  ),
                },
              },
            },
          );

          const incidents: {items: [{state: string}]; total: number} =
            await response.json();

          return (
            incidents.total > 0 &&
            incidents.items.filter(({state}) => state === 'PENDING').length ===
              0
          );
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toBeTruthy(),
  ]);
});

test.describe('Process Instance', () => {
  test('Resolve an incident', async ({page, processInstancePage}) => {
    test.setTimeout(DEFAULT_TEST_TIMEOUT + 3 * 15000); // 15 seconds for each applied operation in this test

    await processInstancePage.navigateToProcessInstance({
      id: initialData.instanceWithIncidentToResolve.processInstanceKey,
    });

    // click and expand incident bar
    await expect(
      page.getByRole('button', {
        name: /view 2 incidents in instance/i,
      }),
    ).toBeVisible();

    await page
      .getByRole('button', {
        name: /view 2 incidents in instance/i,
      })
      .click();

    await expect(
      page.getByRole('combobox', {name: /filter by incident type/i}),
    ).toBeVisible();
    await expect(
      page.getByRole('combobox', {name: /filter by flow node/i}),
    ).toBeVisible();

    // edit goUp variable
    await processInstancePage.variablesList
      .getByRole('button', {
        name: /edit variable/i,
      })
      .click();

    await processInstancePage.editVariableValueField.clear();
    await processInstancePage.editVariableValueField.type('20');

    await expect(processInstancePage.saveVariableButton).toBeEnabled();
    await processInstancePage.saveVariableButton.click();

    await expect(processInstancePage.variableSpinner).toBeVisible();
    await expect(processInstancePage.variableSpinner).not.toBeVisible();

    // retry one incident to resolve it
    await processInstancePage.incidentsTable
      .getByRole('row', {name: /Condition error/i})
      .getByRole('button', {name: 'Retry Incident'})
      .click();

    await expect(
      processInstancePage.incidentsTable
        .getByRole('row', {name: /Condition error/i})
        .getByTestId('operation-spinner'),
    ).toBeVisible();

    await expect(
      processInstancePage.incidentsTable.getByTestId('operation-spinner'),
    ).not.toBeVisible();

    await expect(
      processInstancePage.incidentsTable.getByRole('row'),
    ).toHaveCount(1);

    await expect(
      processInstancePage.incidentsTable.getByText(/is cool\?/i),
    ).toBeVisible();
    await expect(
      processInstancePage.incidentsTable.getByText(/where to go\?/i),
    ).not.toBeVisible();

    // add variable isCool

    await processInstancePage.addVariableButton.click();

    await processInstancePage.newVariableNameField.type('isCool');
    await processInstancePage.newVariableValueField.type('true');

    await expect(processInstancePage.saveVariableButton).toBeEnabled();

    await processInstancePage.saveVariableButton.click();
    await expect(processInstancePage.variableSpinner).toBeVisible();
    await expect(processInstancePage.variableSpinner).not.toBeVisible();

    // retry second incident to resolve it
    await processInstancePage.incidentsTable
      .getByRole('row', {
        name: /Extract value error/i,
      })
      .getByRole('button', {name: 'Retry Incident'})
      .click();

    await expect(
      processInstancePage.incidentsTable
        .getByRole('row', {
          name: /Extract value error/i,
        })
        .getByTestId('operation-spinner'),
    ).toBeVisible();

    // expect all incidents resolved
    await expect(processInstancePage.incidentsBanner).not.toBeVisible();
    await expect(processInstancePage.incidentsTable).not.toBeVisible();
    await expect(
      processInstancePage.instanceHeader.getByTestId('COMPLETED-icon'),
    ).toBeVisible();
  });

  test('Cancel an instance', async ({page, processInstancePage}) => {
    test.setTimeout(DEFAULT_TEST_TIMEOUT + 1 * 15000); // 15 seconds for each applied operation in this test

    const instanceId =
      initialData.instanceWithIncidentToCancel.processInstanceKey;

    await processInstancePage.navigateToProcessInstance({
      id: initialData.instanceWithIncidentToCancel.processInstanceKey,
    });

    await expect(
      page.getByRole('button', {
        name: /view 3 incidents in instance/i,
      }),
    ).toBeVisible();

    await page
      .getByRole('button', {
        name: `Cancel Instance ${instanceId}`,
      })
      .click();

    await page
      .getByRole('button', {
        name: 'Apply',
      })
      .click();

    await expect(processInstancePage.operationSpinner).toBeVisible();
    await expect(processInstancePage.operationSpinner).not.toBeVisible();

    await expect(
      page.getByRole('button', {
        name: /view 3 incidents in instance/i,
      }),
    ).not.toBeVisible();

    await expect(
      processInstancePage.instanceHeader.getByTestId('CANCELED-icon'),
    ).toBeVisible();

    await expect(
      await processInstancePage.instanceHeader
        .getByTestId('end-date')
        .innerText(),
    ).toMatch(DATE_REGEX);
  });

  test('Should render collapsed sub process and navigate between planes', async ({
    page,
    processInstancePage,
  }) => {
    const {
      instanceHistory,
      diagram,
      diagram: {popover},
    } = processInstancePage;

    await processInstancePage.navigateToProcessInstance({
      id: initialData.collapsedSubProcessInstance.processInstanceKey,
    });

    await page.getByRole('treeitem', {name: 'startEvent'}).click();
    await expect(page.getByText(/execution duration/i)).toBeVisible();

    await instanceHistory
      .locator(
        page.getByRole('treeitem', {
          name: /submit application/i,
        }),
      )
      .click();

    await expect(popover.getByText(/flow node instance key/i)).toBeVisible();
    await expect(diagram.getFlowNode('submit application')).toBeVisible();

    await page.keyboard.press('ArrowRight');
    await instanceHistory
      .getByRole('treeitem', {
        name: /fill form/i,
      })
      .click();

    await expect(diagram.getFlowNode('fill form')).toBeVisible();
    await expect(popover.getByText(/retries left/i)).toBeVisible();

    await diagram.clickFlowNode('collapsedSubProcess');

    await expect(
      popover.getByText(/flow node instance key/i),
    ).not.toBeVisible();
    await expect(diagram.getFlowNode('submit application')).toBeVisible();
    await expect(diagram.getFlowNode('fill form')).not.toBeVisible();

    await instanceHistory
      .locator(
        page.getByRole('treeitem', {
          name: 'startEvent',
          exact: true,
        }),
      )
      .click();

    await expect(popover.getByText(/flow node instance key/i)).toBeVisible();
    await expect(diagram.getFlowNode('submit application')).toBeVisible();

    const drilldownButton = await page.$('.bjs-drilldown');
    await drilldownButton?.click();

    await expect(
      popover.getByText(/flow node instance key/i),
    ).not.toBeVisible();
    await expect(diagram.getFlowNode('fill form')).toBeVisible();
  });
});
