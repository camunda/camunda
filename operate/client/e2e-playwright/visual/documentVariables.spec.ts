/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {join} from 'node:path';
import {readFileSync} from 'node:fs';
import {expect} from '@playwright/test';
import {test} from '../visual-fixtures';
import {
  documentReferenceProcessInstance,
  mockResponses,
} from '../mocks/processInstance';
import {URL_API_PATTERN} from '../constants';
import {clientConfigMock} from '../mocks/clientConfig';

const JSON_DOCUMENT = readFileSync(
  join(import.meta.dirname, '../mocks/resources/test_json.json'),
);
const IMAGE_DOCUMENT = readFileSync(
  join(import.meta.dirname, '../mocks/resources/test_image.png'),
);

test.beforeEach(async ({context}) => {
  await context.route('**/client-config.js', (route) =>
    route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/javascript;charset=UTF-8',
      },
      body: clientConfigMock,
    }),
  );

  // Increased bottom panel height to have all document variables visible in screenshots.
  await context.addInitScript(() => {
    window.localStorage.setItem(
      'panelStates',
      JSON.stringify({'process-detail-vertical-panel': [30, 70]}),
    );
  });
});

test.describe('document variable visualization', () => {
  test('variables panel with document references', async ({
    page,
    processInstancePage,
  }) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: documentReferenceProcessInstance.detail,
        callHierarchy: documentReferenceProcessInstance.callHierarchy,
        elementInstances: documentReferenceProcessInstance.elementInstances,
        statistics: documentReferenceProcessInstance.statistics,
        sequenceFlows: documentReferenceProcessInstance.sequenceFlows,
        variables: documentReferenceProcessInstance.variables,
        xml: documentReferenceProcessInstance.xml,
      }),
    );
    const imageVariable = page.getByTestId('variable-image_doc');

    await processInstancePage.gotoProcessInstancePage({
      key: documentReferenceProcessInstance.detail.processInstanceKey,
    });

    await expect(imageVariable).toBeVisible();
    await expect(page).toHaveScreenshot();
  });

  test('document list modal', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: documentReferenceProcessInstance.detail,
        callHierarchy: documentReferenceProcessInstance.callHierarchy,
        elementInstances: documentReferenceProcessInstance.elementInstances,
        statistics: documentReferenceProcessInstance.statistics,
        sequenceFlows: documentReferenceProcessInstance.sequenceFlows,
        variables: documentReferenceProcessInstance.variables,
        xml: documentReferenceProcessInstance.xml,
      }),
    );
    const listVariable = page.getByTestId('variable-multiple_docs');

    await processInstancePage.gotoProcessInstancePage({
      key: documentReferenceProcessInstance.detail.processInstanceKey,
    });
    await expect(listVariable).toBeVisible();
    await listVariable.getByRole('button', {name: 'View documents'}).click();

    await expect(
      page.getByRole('dialog', {name: '4 documents in multiple_docs'}),
    ).toBeVisible();
    await expect(page).toHaveScreenshot();
  });

  test('JSON preview', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: documentReferenceProcessInstance.detail,
        callHierarchy: documentReferenceProcessInstance.callHierarchy,
        elementInstances: documentReferenceProcessInstance.elementInstances,
        statistics: documentReferenceProcessInstance.statistics,
        sequenceFlows: documentReferenceProcessInstance.sequenceFlows,
        variables: documentReferenceProcessInstance.variables,
        xml: documentReferenceProcessInstance.xml,
      }),
    );
    await page.route('/v2/documents/*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON_DOCUMENT,
      }),
    );
    const jsonVariable = page.getByTestId('variable-json_doc');

    await processInstancePage.gotoProcessInstancePage({
      key: documentReferenceProcessInstance.detail.processInstanceKey,
    });

    await expect(jsonVariable).toBeVisible();
    await jsonVariable.getByRole('button', {name: 'Preview'}).click();
    await processInstancePage.previewEditor.waitForEditorToLoad();
    await processInstancePage.previewEditor.hideCaret();

    await expect(page).toHaveScreenshot();
  });

  test('JSON preview - error state', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: documentReferenceProcessInstance.detail,
        callHierarchy: documentReferenceProcessInstance.callHierarchy,
        elementInstances: documentReferenceProcessInstance.elementInstances,
        statistics: documentReferenceProcessInstance.statistics,
        sequenceFlows: documentReferenceProcessInstance.sequenceFlows,
        variables: documentReferenceProcessInstance.variables,
        xml: documentReferenceProcessInstance.xml,
      }),
    );
    await page.route('/v2/documents/*', (route) =>
      route.fulfill({status: 500}),
    );
    const jsonVariable = page.getByTestId('variable-json_doc');

    await processInstancePage.gotoProcessInstancePage({
      key: documentReferenceProcessInstance.detail.processInstanceKey,
    });

    await expect(jsonVariable).toBeVisible();
    await jsonVariable.getByRole('button', {name: 'Preview'}).click();

    await expect(page.getByText(/Failed to load JSON preview/)).toBeVisible();
    await expect(page).toHaveScreenshot();
  });

  test('image preview', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: documentReferenceProcessInstance.detail,
        callHierarchy: documentReferenceProcessInstance.callHierarchy,
        elementInstances: documentReferenceProcessInstance.elementInstances,
        statistics: documentReferenceProcessInstance.statistics,
        sequenceFlows: documentReferenceProcessInstance.sequenceFlows,
        variables: documentReferenceProcessInstance.variables,
        xml: documentReferenceProcessInstance.xml,
      }),
    );
    await page.route('/v2/documents/*', (route) =>
      route.fulfill({
        status: 200,
        contentType: 'image/png',
        body: IMAGE_DOCUMENT,
      }),
    );
    const imageVariable = page.getByTestId('variable-image_doc');

    await processInstancePage.gotoProcessInstancePage({
      key: documentReferenceProcessInstance.detail.processInstanceKey,
    });

    await expect(imageVariable).toBeVisible();

    await imageVariable.getByRole('button', {name: 'Preview'}).click();

    await expect(page.getByRole('img', {name: 'test_image.png'})).toBeVisible();
    await expect(page).toHaveScreenshot();
  });

  test('image preview - error state', async ({page, processInstancePage}) => {
    await page.route(
      URL_API_PATTERN,
      mockResponses({
        processInstanceDetail: documentReferenceProcessInstance.detail,
        callHierarchy: documentReferenceProcessInstance.callHierarchy,
        elementInstances: documentReferenceProcessInstance.elementInstances,
        statistics: documentReferenceProcessInstance.statistics,
        sequenceFlows: documentReferenceProcessInstance.sequenceFlows,
        variables: documentReferenceProcessInstance.variables,
        xml: documentReferenceProcessInstance.xml,
      }),
    );
    await page.route('/v2/documents/*', (route) =>
      route.fulfill({status: 500}),
    );
    const imageVariable = page.getByTestId('variable-image_doc');

    await processInstancePage.gotoProcessInstancePage({
      key: documentReferenceProcessInstance.detail.processInstanceKey,
    });

    await expect(imageVariable).toBeVisible();

    await imageVariable.getByRole('button', {name: 'Preview'}).click();

    await expect(page.getByText(/Failed to load image preview/)).toBeVisible();
    await expect(page).toHaveScreenshot();
  });
});
