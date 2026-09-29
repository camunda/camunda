/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {buildUrl, credentials, defaultHeaders} from 'utils/http';
import {CREATE_DOCUMENT_REQUEST_WITH_CONTENT_TYPE} from 'utils/beans/requestBeans';
import {generateUniqueId} from 'utils/constants';
import {openDocumentUrl} from 'utils/documentBrowserOutcome';
import {navigateToAppHome} from '@pages/UtilitiesPage';
import {captureScreenshot, captureFailureVideo} from '@setup';

/**
 * A document that announces itself if a browser ever interprets it: the title
 * is rewritten from the markup and again from script. Asserting the title never
 * takes either value is what distinguishes "the browser saved the file" from
 * "the browser rendered attacker-supplied markup in the application origin".
 */
const RENDERED_TITLE_MARKER = 'DOCUMENT-RENDERED';
const SCRIPT_TITLE_MARKER = 'DOCUMENT-SCRIPT-RAN';
const ACTIVE_DOCUMENT_CONTENT =
  `<!doctype html><title>${RENDERED_TITLE_MARKER}</title>` +
  `<script>document.title = '${SCRIPT_TITLE_MARKER}';</script>` +
  `<h1>active content</h1>`;

// 1x1 PNG. The endpoint decides from the stored content type and never sniffs
// the bytes, but a real image keeps the rendered-document assertions honest.
const PNG_BYTES = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAABzenr0AAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg==',
  'base64',
).toString('binary');

type StoredDocument = {url: string; fileName: string};

const documents: Record<string, StoredDocument> = {};

test.beforeAll(async ({request}) => {
  async function store(
    contentType: string,
    content: string,
    extension: string,
  ): Promise<StoredDocument> {
    const fileName = `${generateUniqueId()}.${extension}`;
    const response = await request.post(buildUrl('/documents'), {
      headers: defaultHeaders(),
      multipart: CREATE_DOCUMENT_REQUEST_WITH_CONTENT_TYPE(
        fileName,
        contentType,
        content,
      ),
    });
    expect(response.status()).toBe(201);
    const document = await response.json();
    return {
      fileName,
      url: `${credentials.baseUrl}/v2/documents/${document.documentId}?contentHash=${document.contentHash}`,
    };
  }

  documents.active = await store('text/html', ACTIVE_DOCUMENT_CONTENT, 'html');
  documents.image = await store('image/png', PNG_BYTES, 'png');
});

test.describe('Document Content Browser Rendering', () => {
  test.beforeEach(async ({page}) => {
    // Start from an application page so a document that is saved rather than
    // rendered leaves the tab somewhere recognisable.
    await navigateToAppHome(page, 'operate');
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Active content is downloaded instead of rendered in the application origin', async ({
    page,
  }) => {
    const appUrlBeforeOpening = page.url();

    const outcome = await openDocumentUrl(page, documents.active.url);

    await test.step('the browser saves the document', async () => {
      expect(outcome.downloadedAs).not.toBeNull();
      expect(outcome.rendered).toBe(false);
    });

    await test.step('nothing from the document is interpreted', async () => {
      expect(outcome.title).not.toBe(RENDERED_TITLE_MARKER);
      expect(outcome.title).not.toBe(SCRIPT_TITLE_MARKER);
      expect(page.url()).toBe(appUrlBeforeOpening);
    });
  });

  test('Image content is still rendered in the browser', async ({page}) => {
    const outcome = await openDocumentUrl(page, documents.image.url);

    expect(outcome.downloadedAs).toBeNull();
    expect(outcome.rendered).toBe(true);
    expect(outcome.contentType).toBe('image/png');
  });
});
