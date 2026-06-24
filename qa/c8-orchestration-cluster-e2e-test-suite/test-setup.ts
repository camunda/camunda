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
  if (testInfo.status === 'failed') {
    const video = page.video();
    if (video) {
      testInfo.annotations.push({
        type: 'testrail_attachment',
        description: 'Video recorded for failed test',
      });
    }
  }
}
