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

import {setup} from './processInstanceVariables.mocks';
import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {DEFAULT_TEST_TIMEOUT, SETUP_WAITING_TIME} from './constants';
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
            `${config.endpoint}/v1/process-instances/${initialData.instance.processInstanceKey}`,
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
            `${config.endpoint}/v1/process-instances/${initialData.instanceWithManyVariables.processInstanceKey}`,
          );

          return response.status();
        },
        {timeout: SETUP_WAITING_TIME},
      )
      .toBe(200),
  ]);
});

test.describe('Process Instance Variables', () => {
  test('Edit variables', async ({page, processInstancePage}) => {
    test.setTimeout(DEFAULT_TEST_TIMEOUT + 1 * 15000); // 15 seconds for each applied operation in this test

    const processInstanceKey = initialData.instance.processInstanceKey;
    processInstancePage.navigateToProcessInstance({id: processInstanceKey});

    expect(processInstancePage.addVariableButton).toBeEnabled();

    // open process instance page, after clicking the edit variable button see that save variable button is disabled.
    await page
      .getByRole('button', {
        name: /edit variable/i,
      })
      .click();

    // delete the value of the variable and add something else. see that save variable button is enabled, and no spinner is displayed.
    await processInstancePage.editVariableValueField.clear();
    await processInstancePage.editVariableValueField.type('"editedTestValue"');

    // click save variable button and see that both edit variable spinner and operation spinner are displayed.
    await expect(processInstancePage.saveVariableButton).toBeEnabled();
    await processInstancePage.saveVariableButton.click();
    await expect(processInstancePage.variableSpinner).toBeVisible();
    await expect(processInstancePage.operationSpinner).toBeVisible();

    // see that spinners both disappear after save variable operation completes.
    await expect(processInstancePage.variableSpinner).not.toBeVisible();
    await expect(processInstancePage.operationSpinner).not.toBeVisible();

    // refresh the page and see the variable is still there.
    await page.reload();
    await expect(page.getByText('"editedTestValue"')).toBeVisible();
  });

  test('Add variables', async ({page, processInstancePage, processesPage}) => {
    test.setTimeout(DEFAULT_TEST_TIMEOUT + 1 * 15000); // 15 seconds for each applied operation in this test

    const processInstanceKey = initialData.instance.processInstanceKey;
    processInstancePage.navigateToProcessInstance({id: processInstanceKey});

    expect(processInstancePage.addVariableButton).toBeEnabled();

    // add a new variable
    await processInstancePage.addVariableButton.click();
    await processInstancePage.newVariableNameField.type('secondTestKey');
    await processInstancePage.newVariableValueField.type('"secondTestValue"');
    await expect(processInstancePage.saveVariableButton).toBeEnabled();

    // click save variable button and see that both variable spinner and operation spinner are displayed.
    await processInstancePage.saveVariableButton.click();
    await expect(processInstancePage.variableSpinner).toBeVisible();
    await expect(processInstancePage.operationSpinner).toBeVisible();

    // see that spinners both disappear after save variable operation completes
    await expect(processInstancePage.variableSpinner).not.toBeVisible();
    await expect(processInstancePage.operationSpinner).not.toBeVisible();

    // refresh the page and see the variable is still there.
    await page.reload();
    await expect(page.getByText('secondTestKey', {exact: true})).toBeVisible();
    await expect(page.getByText('"secondTestValue"')).toBeVisible();

    // go to instance page, filter and find the instance by added variable
    await page.getByRole('link', {name: /processes/i}).click();

    await processesPage.displayOptionalFilter('Process Instance Key(s)');
    await processesPage.displayOptionalFilter('Variable');

    await processesPage.variableNameFilter.type('secondTestKey');
    await processesPage.variableValueFilter.type('"secondTestValue"');
    await processesPage.processInstanceKeysFilter.type(processInstanceKey);

    await expect(page.getByText('1 result')).toBeVisible();

    await expect(
      page.getByRole('link', {
        name: `View instance ${processInstanceKey}`,
      }),
    ).toBeVisible();
  });

  test('Infinite scrolling', async ({page, processInstancePage}) => {
    const processInstanceKey =
      initialData.instanceWithManyVariables.processInstanceKey;

    processInstancePage.navigateToProcessInstance({id: processInstanceKey});

    await expect(processInstancePage.variablesList).toBeVisible();

    await expect(
      processInstancePage.variablesList.getByRole('row'),
    ).toHaveCount(51);

    await expect(page.getByText('aa', {exact: true})).toBeVisible();
    await expect(page.getByText('bx', {exact: true})).toBeVisible();

    await page.getByText('bx', {exact: true}).scrollIntoViewIfNeeded();

    await expect(
      processInstancePage.variablesList.getByRole('row'),
    ).toHaveCount(101);

    await expect(page.getByText('aa', {exact: true})).toBeVisible();
    await expect(page.getByText('dv', {exact: true})).toBeVisible();

    await page.getByText('dv', {exact: true}).scrollIntoViewIfNeeded();

    await expect(
      processInstancePage.variablesList.getByRole('row'),
    ).toHaveCount(151);

    await expect(page.getByText('aa', {exact: true})).toBeVisible();
    await expect(page.getByText('ft', {exact: true})).toBeVisible();

    await page.getByText('ft', {exact: true}).scrollIntoViewIfNeeded();

    await expect(
      processInstancePage.variablesList.getByRole('row'),
    ).toHaveCount(201);

    await expect(page.getByText('aa', {exact: true})).toBeVisible();
    await expect(page.getByText('hr', {exact: true})).toBeVisible();

    await page.getByText('hr', {exact: true}).scrollIntoViewIfNeeded();

    await expect(
      processInstancePage.variablesList.getByRole('row'),
    ).toHaveCount(201);

    await expect(page.getByText('aa', {exact: true})).not.toBeVisible();
    await expect(page.getByText('by', {exact: true})).toBeVisible();
    await expect(page.getByText('jp', {exact: true})).toBeVisible();

    await page.getByText('by', {exact: true}).scrollIntoViewIfNeeded();
    await expect(
      processInstancePage.variablesList.getByRole('row'),
    ).toHaveCount(201);

    await expect(page.getByText('jp', {exact: true})).not.toBeVisible();
    await expect(page.getByText('aa', {exact: true})).toBeVisible();
    await expect(page.getByText('by', {exact: true})).toBeVisible();
  });
});
