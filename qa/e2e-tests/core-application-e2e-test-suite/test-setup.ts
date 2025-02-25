import {Page, TestInfo} from '@playwright/test';
import {randomUUID} from 'crypto';
import axios from 'axios';

export async function captureScreenshot(page: Page, testInfo: TestInfo) {
  const screenshotPath = `test-results/screenshots/screenshot-${randomUUID()}.png`;
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
    const videoPath = `test-results/videos/video-${testInfo.title}-chromium.webm`;
    const video = await page.video();
    if (video) {
      await video.saveAs(videoPath);
      testInfo.annotations.push({
        type: 'testrail_attachment',
        description: videoPath,
      });
    }
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
