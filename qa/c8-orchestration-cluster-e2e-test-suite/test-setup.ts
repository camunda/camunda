/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, TestInfo} from '@playwright/test';
import {randomUUID} from 'crypto';
import path from 'path';

export async function captureScreenshot(page: Page, testInfo: TestInfo) {
  // Capture only when the test did not pass. Skip explicitly on
  // passed/skipped rather than gating on `=== 'failed'`, so a timed-out or
  // interrupted test -- exactly the cases where a screenshot is most useful --
  // still gets one.
  if (testInfo.status === 'passed' || testInfo.status === 'skipped') {
    return;
  }
  const screenshotFileName = `screenshot-${randomUUID()}.png`;
  const screenshotPath = path.resolve(testInfo.outputDir, screenshotFileName);
  await page.screenshot({
    path: screenshotPath,
    fullPage: true,
    timeout: 200000,
  });

  await testInfo.attach(`screenshot-${testInfo.title}`, {
    path: screenshotPath,
    contentType: 'image/png',
  });
}

export async function captureFailureVideo(page: Page, testInfo: TestInfo) {
  // Same policy as captureScreenshot above: skip on passed/skipped rather
  // than requiring literally 'failed', so timed-out/interrupted tests keep
  // getting a video too, and the two attachment types stay in sync.
  //
  // No manual attach step needed: Playwright's `video: 'retain-on-failure'`
  // config (playwright.config.ts) already captures and attaches the video
  // to the HTML report on its own.
  if (testInfo.status === 'passed' || testInfo.status === 'skipped') {
    return;
  }
}
