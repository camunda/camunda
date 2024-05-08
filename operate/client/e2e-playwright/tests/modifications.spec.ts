/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {setup} from './modifications.mocks';
import {test} from '../test-fixtures';
import {expect} from '@playwright/test';
import {config} from '../config';
import {SETUP_WAITING_TIME} from './constants';

let initialData: Awaited<ReturnType<typeof setup>>;

test.beforeAll(async ({request}) => {
  initialData = await setup();
  test.setTimeout(SETUP_WAITING_TIME);

  await expect
    .poll(
      async () => {
        const response = await request.get(
          `${config.endpoint}/v1/process-instances/${initialData.instanceWithoutAnIncident.processInstanceKey}`,
        );

        return response.status();
      },
      {timeout: SETUP_WAITING_TIME},
    )
    .toBe(200);
});

test.beforeEach(async ({page, processInstancePage}) => {
  await page.addInitScript(() => {
    window.localStorage.setItem(
      'sharedState',
      JSON.stringify({hideModificationHelperModal: true}),
    );
  });

  await processInstancePage.navigateToProcessInstance({
    id: initialData.instanceWithoutAnIncident.processInstanceKey,
  });
});

test.describe('Modifications', () => {
  test('Should apply/remove edit variable modifications', async ({
    processInstancePage,
    page,
  }) => {
    await expect(page.getByTestId('variable-foo')).toBeVisible();
    await page
      .getByRole('button', {
        name: /modify instance/i,
      })
      .click();

    await expect(
      page.getByText('Process Instance Modification Mode'),
    ).toBeVisible();

    await processInstancePage.getEditVariableFieldSelector('foo').clear();
    await processInstancePage.getEditVariableFieldSelector('foo').type('1');

    page.keyboard.press('Tab');

    await expect(page.getByText('Last added modification:')).toBeVisible();
    await expect(page.getByText(/edit variable "foo"/i)).toBeVisible();

    await processInstancePage.getEditVariableFieldSelector('test').clear();
    await processInstancePage.getEditVariableFieldSelector('test').type('2');

    page.keyboard.press('Tab');

    await expect(page.getByText('Last added modification:')).toBeVisible();
    await expect(page.getByText(/edit variable "test"/i)).toBeVisible();

    await processInstancePage.getEditVariableFieldSelector('foo').clear();
    await processInstancePage.getEditVariableFieldSelector('foo').type('3');

    page.keyboard.press('Tab');

    await expect(page.getByText('Last added modification:')).toBeVisible();
    await expect(page.getByText(/edit variable "foo"/i)).toBeVisible();

    await expect(
      processInstancePage.getEditVariableFieldSelector('foo'),
    ).toHaveValue('3');

    // Undo last edit variable modification, and see value is updated to the previous value
    await processInstancePage.undoModification();

    await expect(
      processInstancePage.getEditVariableFieldSelector('foo'),
    ).toHaveValue('1');

    await expect(page.getByText('Last added modification:')).toBeVisible();
    await expect(page.getByText(/edit variable "test"/i)).toBeVisible();

    // Undo after navigating to another flow node, and see values are correct in the previous scope

    await processInstancePage.instanceHistory.getByText(/never fails/i).click();
    await expect(
      page.getByText(/The Flow Node has no Variables/i),
    ).toBeVisible();

    await processInstancePage.undoModification();

    await expect(page.getByText('Last added modification:')).toBeVisible();
    await expect(page.getByText(/edit variable "foo"/i)).toBeVisible();

    await processInstancePage.instanceHistory
      .getByText(/without incidents process/i)
      .click();

    await expect(page.getByTestId('variable-foo')).toBeVisible();

    await expect(
      processInstancePage.getEditVariableFieldSelector('test'),
    ).toHaveValue('123');

    await expect(
      processInstancePage.getEditVariableFieldSelector('foo'),
    ).toHaveValue('1');

    // Undo again, see last modification footer disappear, all variables have their initial values
    await processInstancePage.undoModification();

    await expect(page.getByText('Last added modification:')).not.toBeVisible();

    await expect(
      processInstancePage.getEditVariableFieldSelector('test'),
    ).toHaveValue('123');

    await expect(
      processInstancePage.getEditVariableFieldSelector('foo'),
    ).toHaveValue('"bar"');

    // should edit a variable, remove it from the summary modal, see it disappeared from the variables panel and footer
    await processInstancePage.getEditVariableFieldSelector('foo').clear();
    await (
      await processInstancePage.getEditVariableFieldSelector('foo')
    ).type('1');

    await processInstancePage.getEditVariableFieldSelector('foo').clear();
    await (
      await processInstancePage.getEditVariableFieldSelector('foo')
    ).type('2');

    page.keyboard.press('Tab');

    await page
      .getByRole('button', {
        name: /apply modifications/i,
      })
      .click();

    await expect(page.getByText('foo: 2')).toBeVisible();
    await page
      .getByRole('button', {
        name: /delete variable modification/i,
      })
      .click();

    await page.getByRole('button', {name: 'Cancel'}).click();

    await expect(page.getByText('Last added modification:')).not.toBeVisible();
    await expect(
      processInstancePage.getEditVariableFieldSelector('foo'),
    ).toHaveValue('"bar"');
  });

  test('Should apply/remove add variable modifications', async ({
    processInstancePage,
    page,
  }) => {
    await expect(page.getByTestId('variable-foo')).toBeVisible();
    await page
      .getByRole('button', {
        name: /modify instance/i,
      })
      .click();

    await expect(
      page.getByText('Process Instance Modification Mode'),
    ).toBeVisible();

    // add a new variable
    await page
      .getByRole('button', {
        name: /add variable/i,
      })
      .click();
    await expect(page.getByTestId('variable-newVariables[0]')).toBeVisible();

    await processInstancePage
      .getNewVariableNameFieldSelector('newVariables[0]')
      .clear();
    await processInstancePage
      .getNewVariableNameFieldSelector('newVariables[0]')
      .type('test2');

    page.keyboard.press('Tab');

    await processInstancePage
      .getNewVariableValueFieldSelector('newVariables[0]')
      .type('1');

    page.keyboard.press('Tab');

    await expect(page.getByText('Last added modification:')).toBeVisible();
    await expect(page.getByText(/add new variable "test2"/i)).toBeVisible();

    // add another new variable

    await page
      .getByRole('button', {
        name: /add variable/i,
      })
      .click();
    await expect(page.getByTestId('variable-newVariables[1]')).toBeVisible();

    await processInstancePage
      .getNewVariableNameFieldSelector('newVariables[1]')
      .type('test3');

    page.keyboard.press('Tab');

    await processInstancePage
      .getNewVariableValueFieldSelector('newVariables[1]')
      .type('2');
    page.keyboard.press('Tab');

    await expect(page.getByText('Last added modification:')).toBeVisible();
    await expect(page.getByText(/add new variable "test3"/i)).toBeVisible();

    // edit first added variable

    await processInstancePage
      .getNewVariableNameFieldSelector('newVariables[0]')
      .clear();
    await processInstancePage
      .getNewVariableNameFieldSelector('newVariables[0]')
      .type('test2-edited');

    page.keyboard.press('Tab');

    await expect(page.getByText('Last added modification:')).toBeVisible();
    await expect(
      page.getByText(/add new variable "test2-edited"/i),
    ).toBeVisible();

    await expect(
      processInstancePage.getNewVariableNameFieldSelector('newVariables[0]'),
    ).toHaveValue('test2-edited');

    await expect(
      processInstancePage.getNewVariableNameFieldSelector('newVariables[1]'),
    ).toHaveValue('test3');

    // Undo last edit variable modification, and see value is updated to the previous value
    await processInstancePage.undoModification();

    await expect(
      processInstancePage.getNewVariableNameFieldSelector('newVariables[0]'),
    ).toHaveValue('test2');

    await expect(page.getByText('Last added modification:')).toBeVisible();
    await expect(page.getByText(/add new variable "test3"/i)).toBeVisible();

    // Undo after navigating to another flow node, and see last added variable is removed

    await processInstancePage.instanceHistory.getByText(/never fails/i).click();
    await expect(
      page.getByText(/The Flow Node has no Variables/i),
    ).toBeVisible();

    await processInstancePage.undoModification();
    await expect(page.getByText('Last added modification:')).toBeVisible();
    await expect(page.getByText(/add new variable "test2"/i)).toBeVisible();

    await processInstancePage.instanceHistory
      .getByText(/without incidents process/i)
      .click();

    await expect(page.getByTestId('variable-newVariables[0]')).toBeVisible();
    expect(page.getByTestId('newVariables[1]')).not.toBeVisible();

    await expect(
      processInstancePage.getNewVariableNameFieldSelector('newVariables[0]'),
    ).toHaveValue('test2');

    await expect(
      processInstancePage.getNewVariableValueFieldSelector('newVariables[0]'),
    ).toHaveValue('1');

    // Undo again, see last modification footer disappear, new variable field is removed

    await processInstancePage.undoModification();
    await expect(page.getByText('Last added modification:')).not.toBeVisible();
    await expect(
      page.getByTestId('variable-newVariables[0]'),
    ).not.toBeVisible();

    // should add 2 new variables with same name and value to different scopes, remove one of it from the summary modal, see it disappeared from the variables panel and footer

    await page
      .getByRole('button', {
        name: /add variable/i,
      })
      .click();
    await expect(page.getByTestId('variable-newVariables[0]')).toBeVisible();

    await processInstancePage
      .getNewVariableNameFieldSelector('newVariables[0]')
      .type('test2');

    page.keyboard.press('Tab');

    await processInstancePage
      .getNewVariableValueFieldSelector('newVariables[0]')
      .type('1');

    page.keyboard.press('Tab');

    await processInstancePage.instanceHistory.getByText(/never fails/i).click();

    await expect(
      page.getByText(/The Flow Node has no Variables/i),
    ).toBeVisible();

    await page
      .getByRole('button', {
        name: /add variable/i,
      })
      .click();

    await expect(page.getByTestId('variable-newVariables[0]')).toBeVisible();

    await processInstancePage
      .getNewVariableNameFieldSelector('newVariables[0]')
      .type('test2');

    page.keyboard.press('Tab');

    await processInstancePage
      .getNewVariableValueFieldSelector('newVariables[0]')
      .type('1');

    page.keyboard.press('Tab');

    await page
      .getByRole('button', {
        name: /apply modifications/i,
      })
      .click();

    await expect(page.getByRole('dialog').getByText('test2: 1')).toHaveCount(2);

    await page
      .getByRole('dialog')
      .getByRole('button', {
        name: 'Delete variable modification',
      })
      .nth(1)
      .click();

    await page
      .getByRole('dialog')
      .getByRole('button', {name: 'Cancel'})
      .click();

    await expect(
      page.getByTestId('variable-newVariables[0]'),
    ).not.toBeVisible();

    await processInstancePage.instanceHistory
      .getByText(/without incidents process/i)
      .click();

    await expect(page.getByTestId('variable-newVariables[0]')).toBeVisible();

    // should change the new added variables value, remove one it from the summary modal, see it disappeared from the variables panel and footer

    await processInstancePage
      .getNewVariableValueFieldSelector('newVariables[0]')
      .type('2');

    page.keyboard.press('Tab');

    await page
      .getByRole('button', {
        name: /apply modifications/i,
      })
      .click();

    await expect(page.getByText('test2: 21')).toBeVisible();

    await page
      .getByRole('button', {
        name: /delete variable modification/i,
      })
      .click();

    await page.getByRole('button', {name: 'Cancel'}).click();

    await expect(page.getByText('Last added modification:')).not.toBeVisible();
    await expect(
      page.getByTestId('variable-newVariables[0]'),
    ).not.toBeVisible();
  });
});
