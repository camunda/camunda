/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
    const {instanceHistory, popover, diagram} = processInstancePage;

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
    await expect(diagram.getByText(/submit application/i)).toBeVisible();

    await page.keyboard.press('ArrowRight');
    await instanceHistory
      .getByRole('treeitem', {
        name: /fill form/i,
      })
      .click();

    await expect(diagram.getByText(/fill form/i)).toBeVisible();
    await expect(popover.getByText(/retries left/i)).toBeVisible();

    await diagram.getByText(/collapsedSubProcess/i).click();

    await expect(
      popover.getByText(/flow node instance key/i),
    ).not.toBeVisible();
    await expect(diagram.getByText(/submit application/i)).toBeVisible();
    await expect(diagram.getByText(/fill form/i)).not.toBeVisible();

    await instanceHistory
      .locator(
        page.getByRole('treeitem', {
          name: 'startEvent',
          exact: true,
        }),
      )
      .click();

    await expect(popover.getByText(/flow node instance key/i)).toBeVisible();
    await expect(diagram.getByText(/submit application/i)).toBeVisible();

    const drilldownButton = await page.$('.bjs-drilldown');
    await drilldownButton?.click();

    await expect(
      popover.getByText(/flow node instance key/i),
    ).not.toBeVisible();
    await expect(diagram.getByText(/fill form/i)).toBeVisible();
  });
});
