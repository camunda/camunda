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
