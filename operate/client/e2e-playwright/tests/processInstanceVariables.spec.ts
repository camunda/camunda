/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {setup} from './processInstanceVariables.mocks';
import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {SETUP_WAITING_TIME} from './constants';
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
  test('Edit variables @roundtrip', async ({page, processInstancePage}) => {
    test.slow();

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

  test('Add variables @roundtrip', async ({
    page,
    processInstancePage,
    processesPage,
    processesPage: {filtersPanel},
  }) => {
    test.slow();

    const processInstanceKey = initialData.instance.processInstanceKey;
    processInstancePage.navigateToProcessInstance({id: processInstanceKey});

    await expect(processInstancePage.addVariableButton).toBeEnabled();

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

    await filtersPanel.displayOptionalFilter('Process Instance Key(s)');
    await filtersPanel.displayOptionalFilter('Variable');

    await filtersPanel.variableNameFilter.type('secondTestKey');
    await filtersPanel.variableValueFilter.type('"secondTestValue"');
    await filtersPanel.processInstanceKeysFilter.type(processInstanceKey);

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
