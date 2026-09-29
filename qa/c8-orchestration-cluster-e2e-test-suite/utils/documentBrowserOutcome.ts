/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Page} from '@playwright/test';

/**
 * Only spent when the browser neither renders nor saves, which is a failure the
 * assertions should report rather than hang on.
 */
const DOWNLOAD_TIMEOUT_MS = 15_000;

export type DocumentOpenOutcome = {
  /** Suggested filename when the browser saved the response, otherwise null. */
  downloadedAs: string | null;
  /** True when the tab ended up showing the document itself. */
  rendered: boolean;
  /** Content type of the rendered document, null when nothing was rendered. */
  contentType: string | null;
  /** Title of whatever the tab shows afterwards. */
  title: string;
};

/**
 * Opens a document URL the way a user following a document link would, and
 * reports whether the browser saved it or rendered it.
 *
 * Engines disagree on how a navigation that turns into a download surfaces:
 * Chromium rejects the navigation with "Download is starting" while Firefox
 * resolves it and fires only the download event. Neither is judged here — the
 * outcome is derived from the download event and the resulting document, so
 * the same assertions hold across the browsers this suite runs.
 */
export async function openDocumentUrl(
  page: Page,
  documentUrl: string,
): Promise<DocumentOpenOutcome> {
  const documentPath = documentUrl.split('?')[0];

  // Subscribed before the navigation starts, because Chromium rejects the
  // navigation before it emits the download event.
  const download = page
    .waitForEvent('download', {timeout: DOWNLOAD_TIMEOUT_MS})
    .then((event) => event.suggestedFilename() || 'unnamed')
    .catch(() => null);

  const navigated = await page
    .goto(documentUrl)
    .then(() => true)
    .catch(() => false);

  if (navigated && page.url().startsWith(documentPath)) {
    // The tab is showing the document, so there is no download to wait for.
    return {
      downloadedAs: null,
      rendered: true,
      contentType: await page.evaluate(() => document.contentType),
      title: await page.title(),
    };
  }

  return {
    downloadedAs: await download,
    rendered: false,
    contentType: null,
    title: await page.title(),
  };
}
