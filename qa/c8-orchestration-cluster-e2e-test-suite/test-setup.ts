/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, TestInfo} from '@playwright/test';
import {randomUUID} from 'crypto';
import axios from 'axios';
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
  const videoFileName = `video-${randomUUID()}.webm`;
  const videoPath = path.resolve(testInfo.outputDir, videoFileName);
  const video = page.video();
  if (video) {
    await video.saveAs(videoPath);
    testInfo.annotations.push({
      type: 'testrail_attachment',
      description: videoPath,
    });
  }
}

export function generateRandomStringAsync(length: number): Promise<string> {
  // Simulate an asynchronous operation (e.g., using setTimeout)
  return new Promise<string>((resolve) => {
    setTimeout(() => {
      const alphabet = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ';
      let result = '';
      for (let i = 0; i < length; i++) {
        const randomIndex = Math.floor(Math.random() * alphabet.length);
        result += alphabet.charAt(randomIndex);
      }
      resolve(result);
    }, 100); // Simulate a delay of 100 milliseconds
  });
}

export async function performBasicAuthPostRequest(
  requestUrl: string,
  username: string,
  password: string,
) {
  // Perform API authentication request here
  try {
    const authResponse = await axios.post(requestUrl, {
      auth: {
        username: username,
        password: password,
      },
    });

    // Check if authentication was successful (e.g., check response status, tokens, etc.)
    if (authResponse.status === 200) {
      console.log('Authentication successful.');
    } else {
      console.error('Authentication failed.');
      // Handle authentication failure
    }
  } catch (error) {
    console.error('Authentication request error:', error);
    // Handle authentication request errors
  }
}

export async function performBearerTokenAuthPostRequest(
  requestUrl: string,
  bearerToken: string,
) {
  try {
    const authToken = bearerToken;
    const apiUrl = requestUrl;

    const response = await axios.post(apiUrl, {
      headers: {
        Authorization: `Bearer ${authToken}`,
      },
    });

    // Check if the request was successful (e.g., check response status, handle response data)
    if (response.status === 200) {
      console.log('Bearer Token API request successful.');
      // Handle response data if needed
      console.log('Response Data:', response.data);
    } else {
      console.error('Bearer Token API request failed.');
      // Handle request failure
    }
  } catch (error) {
    console.error('Bearer Token API request error:', error);
    // Handle request errors
  }
}
