/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect, Page} from '@playwright/test';
import {LoginPage} from '@pages/LoginPage';
import {credentials} from '../../utils/http';
import {LOGIN_CREDENTIALS} from '../../utils/constants';

const SWAGGER_UI_URL = '/swagger-ui/index.html';
const API_DOCS_URL = '/v3/api-docs';
const CSRF_SESSION_STORAGE_KEY = 'X-CSRF-TOKEN';
const LICENSE_API_PATH = '/v2/license';

async function loginAndGoToSwaggerUI(page: Page) {
  await page.goto('/operate/login');
  const loginPage = new LoginPage(page);
  await loginPage.login(LOGIN_CREDENTIALS.username, LOGIN_CREDENTIALS.password);
  await page.goto(SWAGGER_UI_URL);
}

test.describe('Swagger UI Tests', () => {
  test('Swagger UI is accessible and presents the API list', async ({page}) => {
    await loginAndGoToSwaggerUI(page);

    // Verify the main Swagger UI container is rendered
    await expect(page.locator('.swagger-ui')).toBeVisible({timeout: 30_000});

    // Verify that at least one API section/tag is listed
    const firstTag = page.locator('.opblock-tag').first();
    await expect(firstTag).toBeVisible({timeout: 30_000});
  });

  test('OpenAPI spec endpoint returns valid JSON (not Base64-encoded)', async ({
    request,
  }) => {
    const response = await request.get(
      `${credentials.baseUrl}${API_DOCS_URL}`,
      {
        headers: {
          Accept: 'application/json',
          Authorization: `Basic ${credentials.accessToken}`,
        },
      },
    );

    expect(response.status()).toBe(200);

    const contentType = response.headers()['content-type'];
    expect(contentType).toContain('application/json');

    const body = await response.json();

    // Verify the spec has the expected OpenAPI structure (not a Base64 string)
    expect(typeof body).toBe('object');
    expect(body).toHaveProperty('openapi');
    expect(body).toHaveProperty('info');
    expect(body).toHaveProperty('paths');
  });

  test('CSRF token in session storage is set and not null after login', async ({
    page,
  }) => {
    await loginAndGoToSwaggerUI(page);

    // Wait for the Swagger UI to fully initialize
    await expect(page.locator('.swagger-ui')).toBeVisible({timeout: 30_000});
    await expect(page.locator('.opblock-tag').first()).toBeVisible({
      timeout: 30_000,
    });

    // Evaluate session storage for the CSRF token
    const csrfToken = await page.evaluate((key) => {
      return sessionStorage.getItem(key);
    }, CSRF_SESSION_STORAGE_KEY);

    // The CSRF token must be present and must not be the string "null"
    expect(csrfToken).not.toBeNull();
    expect(csrfToken).not.toBe('null');
    expect(csrfToken!.length).toBeGreaterThan(0);
  });

  test('CSRF token header is filled and not null in sample API command', async ({
    page,
  }) => {
    await loginAndGoToSwaggerUI(page);

    // Wait for Swagger UI to fully initialize
    await expect(page.locator('.swagger-ui')).toBeVisible({timeout: 30_000});
    await expect(page.locator('.opblock-tag').first()).toBeVisible({
      timeout: 30_000,
    });

    // Find the GET /v2/license operation block and expand it
    const licenseOperation = page
      .locator('.opblock.opblock-get')
      .filter({hasText: LICENSE_API_PATH});
    await licenseOperation.click();

    // Click "Try it out"
    const tryItOutButton = licenseOperation.getByRole('button', {
      name: /try it out/i,
    });
    await expect(tryItOutButton).toBeVisible({timeout: 10_000});
    await tryItOutButton.click();

    // Click "Execute" to send the request, wait for the outgoing API request
    const executeButton = licenseOperation.getByRole('button', {
      name: /execute/i,
    });
    await expect(executeButton).toBeVisible({timeout: 10_000});

    // Set up request interception just before clicking Execute, so we capture
    // the specific license API request triggered by the Swagger UI "Execute" action
    const licenseRequestPromise = page.waitForRequest(
      (req) => req.url().includes(LICENSE_API_PATH),
      {timeout: 15_000},
    );
    await executeButton.click();
    const licenseRequest = await licenseRequestPromise;

    // Wait for a response to appear, confirming the request completed
    const responseSection = licenseOperation.locator('.responses-inner');
    await expect(responseSection).toBeVisible({timeout: 15_000});

    // The request must include a CSRF token that is not "null"
    const csrfTokenHeader = licenseRequest.headers()['x-csrf-token'];
    expect(csrfTokenHeader).toBeDefined();
    expect(csrfTokenHeader).not.toBe('null');
    expect(csrfTokenHeader!.length).toBeGreaterThan(0);
  });
});
