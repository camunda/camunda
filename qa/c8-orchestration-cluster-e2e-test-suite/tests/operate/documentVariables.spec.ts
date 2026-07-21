/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {deploy, createSingleInstance} from 'utils/zeebeClient';
import {buildUrl, defaultHeaders} from 'utils/http';
import {CREATE_ON_FLY_DOCUMENT_REQUEST_BODY_WITH_METADATA} from 'utils/beans/requestBeans';
import {generateUniqueId} from 'utils/constants';
import {captureScreenshot, captureFailureVideo} from '@setup';

const VARIABLE_NAME = 'reference_document';
const PROCESS_ID = 'Job_Worker_Process';

type DocumentReference = {
  'camunda.document.type': string;
  storeId: string;
  documentId: string;
  contentHash: string;
  metadata: {
    fileName: string;
    contentType: string;
    size: number;
  };
};

let processInstanceKey: string;
let documentReference: DocumentReference;

test.beforeAll(async ({request}) => {
  await deploy(['./resources/Job_Worker_Process.bpmn']);

  const fileName = generateUniqueId();
  const response = await request.post(buildUrl('/documents'), {
    headers: defaultHeaders(),
    multipart: CREATE_ON_FLY_DOCUMENT_REQUEST_BODY_WITH_METADATA(
      fileName,
      PROCESS_ID,
    ),
  });
  expect(response.status()).toBe(201);
  documentReference = await response.json();

  const instance = await createSingleInstance(PROCESS_ID, 1, {
    [VARIABLE_NAME]: documentReference,
  });
  processInstanceKey = String(instance.processInstanceKey);
});

test.describe('Process Instance Document Variables', () => {
  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('View and download a document reference variable', async ({
    page,
    operateProcessInstancePage,
  }) => {
    test.slow();

    const documentRow = operateProcessInstancePage.variablesList.getByTestId(
      `variable-${VARIABLE_NAME}`,
    );
    const previewButton = documentRow.getByRole('button', {name: 'Preview'});
    const downloadButton = documentRow.getByRole('link', {name: 'Download'});

    await test.step('Open the process instance and its variables', async () => {
      await expect(async () => {
        await operateProcessInstancePage.gotoProcessInstancePage({
          id: processInstanceKey,
        });
        await expect(operateProcessInstancePage.instanceHeader).toBeVisible({
          timeout: 15000,
        });
      }).toPass({timeout: 90000});

      await operateProcessInstancePage.variablesTabButton.click();
      await expect(operateProcessInstancePage.variablesList).toBeVisible({
        timeout: 60000,
      });
    });

    await test.step('Verify the reference is rendered as a document cell', async () => {
      await expect(documentRow).toBeVisible({timeout: 60000});
      await expect(
        documentRow.getByTitle(documentReference.metadata.fileName),
      ).toBeVisible();
    });

    await test.step('Verify preview is unavailable for a text document while download is enabled', async () => {
      await expect(previewButton).toBeDisabled();
      await expect(downloadButton).toBeEnabled();
    });

    await test.step('Download the document', async () => {
      const downloadPromise = page.waitForEvent('download');
      await downloadButton.click();
      const download = await downloadPromise;
      expect(download.suggestedFilename()).toBe(
        documentReference.metadata.fileName,
      );
    });
  });
});
