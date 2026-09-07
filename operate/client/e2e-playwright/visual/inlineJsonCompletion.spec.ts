/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from '@/visual-fixtures';
import {mockResponses, runningInstance} from '@/mocks/processInstance';
import {URL_API_PATTERN} from '@/constants';
import {clientConfigMock} from '@/mocks/clientConfig';

test.beforeEach(async ({page, context, processInstancePage}) => {
  await context.route('**/client-config.js', (route) =>
    route.fulfill({
      status: 200,
      headers: {'Content-Type': 'text/javascript;charset=UTF-8'},
      body: clientConfigMock,
    }),
  );
  await page.route(
    URL_API_PATTERN,
    mockResponses({
      processInstanceDetail: runningInstance.detail,
      callHierarchy: runningInstance.callHierarchy,
      elementInstances: runningInstance.elementInstances,
      statistics: runningInstance.statistics,
      sequenceFlows: runningInstance.sequenceFlows,
      variables: [],
      xml: runningInstance.xml,
    }),
  );
  await processInstancePage.gotoProcessInstancePage({
    key: runningInstance.detail.processInstanceKey,
  });
  await processInstancePage.addVariableButton.click();
  await processInstancePage.newVariableNameField.fill('newPayload');
});

const completionCases = [
  {name: 'empty document', value: '|', label: 'Empty object'},
  {name: 'schema property', value: '{|}', label: '$schema'},
  {
    name: 'reused property',
    value: '[{"status":"active"},{"sta|"}]',
    label: 'status',
  },
  {
    name: 'reused value',
    value: '[{"status":"active"},{"status": |}]',
    label: '"active"',
  },
  {
    name: 'document word',
    value: '{"first":"hello","second":"he|"}',
    label: 'hello',
  },
  {name: 'invalid property', value: '{invalid|}', label: '"invalid"'},
];

for (const {name, value, label} of completionCases) {
  test(`inline JSON completion matches Monaco: ${name}`, async ({page}) => {
    const position = value.indexOf('|');
    const text = value.replace('|', '');
    await page.getByRole('button', {name: 'Open', exact: true}).click();
    await expect(
      page.getByRole('textbox', {name: 'Editor content'}),
    ).toBeFocused();
    await page.keyboard.press('Control+A');
    await page.keyboard.insertText(text);
    await page.keyboard.press('Control+Home');
    for (let index = 0; index < position; index++) {
      await page.keyboard.press('ArrowRight');
    }
    await page.keyboard.press('Control+Space');

    const monacoLabels = page.locator(
      '.suggest-widget.visible .monaco-list-row .label-name',
    );
    await expect(monacoLabels.filter({hasText: label}).first()).toBeVisible();
    const expectedLabels = await monacoLabels.allTextContents();
    await page
      .locator('.suggest-widget.visible')
      .getByText(label, {exact: true})
      .click();
    await expect(page.locator('.suggest-widget.visible')).not.toBeVisible();
    const expectedText = (
      await page.locator('.monaco-editor .view-line').allTextContents()
    )
      .join('\n')
      .replace(/\u00a0/g, ' ');
    await page.getByRole('button', {name: 'Cancel', exact: true}).click();

    const inlineEditor = page.getByRole('textbox', {name: 'Value'});
    await inlineEditor.fill(text);
    await page.keyboard.press('Control+Home');
    for (let index = 0; index < position; index++) {
      await page.keyboard.press('ArrowRight');
    }
    await page.keyboard.press('Control+Space');
    const completions = page.getByRole('listbox');
    await expect(completions).toBeVisible();
    await expect(completions.locator('.cm-completionLabel')).toHaveText(
      expectedLabels,
    );
    await completions.getByText(label, {exact: true}).click();
    await expect(completions).not.toBeVisible();
    await expect
      .poll(async () =>
        (await inlineEditor.locator('.cm-line').allTextContents()).join('\n'),
      )
      .toBe(expectedText);
    await expect(inlineEditor).toBeFocused();
  });
}

test('inline JSON completion preserves Escape and Tab navigation', async ({
  page,
  processInstancePage,
}) => {
  const editor = page.getByRole('textbox', {name: 'Value'});
  await editor.focus();
  await page.keyboard.press('Control+Space');
  await expect(page.getByRole('listbox')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.getByRole('listbox')).not.toBeVisible();
  await expect(editor).not.toBeFocused();

  await editor.focus();
  await page.keyboard.press('Control+Space');
  await expect(page.getByRole('listbox')).toBeVisible();
  await page.keyboard.press('Shift+Tab');
  await expect(processInstancePage.newVariableNameField).toBeFocused();
  await expect(page.getByRole('listbox')).not.toBeVisible();
});

test('inline JSON completion accepts keyboard selection outside the compact editor', async ({
  page,
}) => {
  const editor = page.getByRole('textbox', {name: 'Value'});
  await editor.fill('[{"status":"active"},{"sta');
  await page.keyboard.press('Control+Space');
  const option = page.getByRole('option').filter({hasText: 'status'});
  await expect(option).toBeVisible();
  await expect(option).toBeInViewport();
  expect(
    await option.evaluate((element) => {
      const rect = element.getBoundingClientRect();
      return element.contains(
        document.elementFromPoint(
          rect.left + rect.width / 2,
          rect.top + rect.height / 2,
        ),
      );
    }),
  ).toBe(true);
  await page.keyboard.press('Enter');
  await expect(page.getByRole('listbox')).not.toBeVisible();
  await expect(editor).toHaveText('[{"status":"active"},{"status"');
  await expect(editor).toBeFocused();
});

test('inline JSON completion suggests while typing outside strings', async ({
  page,
}) => {
  const editor = page.getByRole('textbox', {name: 'Value'});
  await editor.fill('[true, false, ');
  await page.keyboard.type('t');
  await expect(
    page.getByRole('option').filter({hasText: 'true'}),
  ).toBeVisible();
  await page.keyboard.press('Enter');
  await expect(editor).toHaveText('[true, false, true');
});
