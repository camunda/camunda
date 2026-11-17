/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Page} from '@playwright/test';

const DOWNLOAD_TIMEOUT_MS = 15_000;

/** Navigation rejections that mean the browser turned the URL into a download. */
const DOWNLOAD_REJECTIONS = ['Download is starting', 'net::ERR_ABORTED'];

export type DocumentOpenOutcome = {
  downloadedAs: string | null;
  rendered: boolean;
  contentType: string | null;
  /** Every title the tab reported while opening, including transient ones. */
  titlesSeen: string[];
};

function samePage(a: string, b: string): boolean {
  // Parsed, not string-prefixed: browsers normalise host case, default ports
  // and trailing slashes, which makes a prefix match report false negatives.
  try {
    const left = new URL(a);
    const right = new URL(b);
    return left.origin === right.origin && left.pathname === right.pathname;
  } catch {
    return false;
  }
}

/**
 * Opens a document URL as a user following a link would, reporting whether the
 * browser saved it or rendered it. Chromium rejects a download-turned-
 * navigation while Firefox resolves it, so the outcome comes from the download
 * event and the resulting document rather than from `goto` itself.
 */
export async function openDocumentUrl(
  page: Page,
  documentUrl: string,
): Promise<DocumentOpenOutcome> {
  const titlesSeen: string[] = [];
  const recordTitle = async () => {
    try {
      titlesSeen.push(await page.title());
    } catch {
      // Mid-navigation; a missed sample is not a failure.
    }
  };
  page.on('load', recordTitle);
  page.on('domcontentloaded', recordTitle);
  page.on('framenavigated', recordTitle);

  // Subscribed before navigating: Chromium rejects before emitting the event.
  const download = page
    .waitForEvent('download', {timeout: DOWNLOAD_TIMEOUT_MS})
    .then((event) => event.suggestedFilename() || 'unnamed')
    .catch(() => null);

  let absorbed: Error | undefined;

  try {
    const navigated = await page
      .goto(documentUrl)
      .then(() => true)
      .catch((error: Error) => {
        // Rethrow anything that cannot be a download, so a refused connection
        // is reported as itself rather than as "the document did not render".
        if (DOWNLOAD_REJECTIONS.some((it) => error.message.includes(it))) {
          absorbed = error;
          return false;
        }
        throw error;
      });

    if (navigated && samePage(page.url(), documentUrl)) {
      await recordTitle();
      return {
        downloadedAs: null,
        rendered: true,
        contentType: await page.evaluate(() => document.contentType),
        titlesSeen,
      };
    }

    const downloadedAs = await download;
    // ERR_ABORTED also covers aborts that are not downloads (a server reset,
    // an interrupting navigation). With no download to show for it, the
    // navigation error is the real failure.
    if (downloadedAs === null && absorbed) {
      throw absorbed;
    }
    await recordTitle();
    return {downloadedAs, rendered: false, contentType: null, titlesSeen};
  } finally {
    page.off('load', recordTitle);
    page.off('domcontentloaded', recordTitle);
    page.off('framenavigated', recordTitle);
  }
}

/** 0 when the bytes did not decode; a broken image still yields a document. */
export async function decodedImageWidth(page: Page): Promise<number> {
  return page.evaluate(() => document.images[0]?.naturalWidth ?? 0);
}
