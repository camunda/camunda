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
  // Capture only when the test did not pass. Screenshots are uploaded to TestRail via the
  // testrail_attachment annotation below and add little value for passing tests. Skip
  // explicitly on passed/skipped rather than gating on `=== 'failed'`, so a timed-out or
  // interrupted test -- exactly the cases where a screenshot is most useful -- still gets one.
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

  testInfo.annotations.push({
    type: 'testrail_attachment',
    description: screenshotPath,
  });
}

export async function captureFailureVideo(page: Page, testInfo: TestInfo) {
  // Same policy as captureScreenshot above: skip on passed/skipped rather
  // than requiring literally 'failed', so timed-out/interrupted tests keep
  // getting a video too, and the two attachment types stay in sync.
  if (testInfo.status === 'passed' || testInfo.status === 'skipped') {
    return;
  }
  const video = page.video();
  if (video) {
    testInfo.annotations.push({
      type: 'testrail_attachment',
      description: 'Video recorded for non-passing test',
    });
  }
}
