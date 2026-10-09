/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {uploadDocument, type StoredDocument} from 'utils/documentFixtures';
import {openDocumentUrl, decodedImageWidth} from '@pages/DocumentContentPage';
import {navigateToAppHome} from '@pages/UtilitiesPage';
import {captureScreenshot, captureFailureVideo} from '@setup';

// Markers a rendered document would put on the tab. Asserted against every
// title seen while opening, so they fail independently of the download check.
const RENDERED_TITLE_MARKER = 'DOCUMENT-RENDERED';
const SCRIPT_TITLE_MARKER = 'DOCUMENT-SCRIPT-RAN';
const ACTIVE_DOCUMENT_CONTENT =
  `<!doctype html><title>${RENDERED_TITLE_MARKER}</title>` +
  `<script>document.title = '${SCRIPT_TITLE_MARKER}';</script>` +
  `<h1>active content</h1>`;

// Kept as bytes: a string part is UTF-8 re-encoded and uploads a corrupt PNG.
// 8x8 rather than 1x1, which all three engines report as naturalWidth 0.
const PNG_WIDTH = 8;
const PNG_BYTES = new Uint8Array(
  Buffer.from(
    'iVBORw0KGgoAAAANSUhEUgAAAAgAAAAICAIAAABLbSncAAAAbElEQVR4nA3JQQEAMAgDMZzgpE7q' +
      'hMf5wAlO6mbLN1VFFypcTLHFFSmqmm7UuJlmm2vSP0QLCYsRK05EP0wbGZsxa87EP4YeNHiYYYcb' +
      'Mj+WXrR4mWWXW7I/jj50+JhjjztyP0IHBYcJGy4kPDtbVkGLfGO6AAAAAElFTkSuQmCC',
    'base64',
  ),
);

const documents: Record<string, StoredDocument> = {};

test.beforeAll(async ({request}) => {
  const [active, image] = await Promise.all([
    uploadDocument(request, 'text/html', ACTIVE_DOCUMENT_CONTENT),
    uploadDocument(request, 'image/png', PNG_BYTES),
  ]);
  documents.active = active;
  documents.image = image;
});

test.describe('Document Content Browser Rendering', () => {
  test.beforeEach(async ({page}) => {
    await navigateToAppHome(page, 'operate');
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Active content is downloaded instead of rendered in the application origin', async ({
    page,
  }) => {
    const outcome = await openDocumentUrl(page, documents.active.url);

    await test.step('the browser saves the document', async () => {
      expect(outcome.downloadedAs).not.toBeNull();
      expect(outcome.rendered).toBe(false);
    });

    await test.step('the tab never navigates to the document', async () => {
      expect(new URL(page.url()).pathname).not.toContain('/v2/documents/');
    });

    await test.step('nothing from the document is ever interpreted', async () => {
      expect(outcome.titlesSeen).not.toContain(RENDERED_TITLE_MARKER);
      expect(outcome.titlesSeen).not.toContain(SCRIPT_TITLE_MARKER);
    });
  });

  test('Image content is still rendered in the browser', async ({page}) => {
    const outcome = await openDocumentUrl(page, documents.image.url);

    expect(outcome.downloadedAs).toBeNull();
    expect(outcome.rendered).toBe(true);
    expect(outcome.contentType).toBe('image/png');

    // A broken image still produces an image document, so check the pixels.
    expect(await decodedImageWidth(page)).toBe(PNG_WIDTH);
  });
});
