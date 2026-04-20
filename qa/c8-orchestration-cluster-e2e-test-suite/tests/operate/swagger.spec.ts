/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from 'fixtures';
import {LoginPage} from '@pages/LoginPage';
import {credentials} from '../../utils/http';
import {LOGIN_CREDENTIALS} from '../../utils/constants';

const SWAGGER_UI_URL = '/swagger-ui/index.html';
const API_DOCS_URL = '/v3/api-docs';
const CSRF_COOKIE_NAME = 'X-CSRF-TOKEN';
const PROCESS_DEFINITION_API_PATH = '/v2/process-definitions/search';
const SWAGGER_UI_CONTAINER_SELECTOR = 'section.swagger-ui.swagger-container';
const PROCESS_DEFINITION_OPERATION_SELECTOR = '.opblock.opblock-post';

test.beforeEach(async ({context}) => {
  // Ensure this scenario is fully unauthenticated even if prior tests logged in.
  await context.clearCookies();
});

test.describe('Swagger UI Tests', () => {
  test('Swagger UI is accessible and presents the API list', async ({page}) => {
    await page.goto(SWAGGER_UI_URL);

    // Verify the main Swagger UI container is rendered
    await expect(page.locator(SWAGGER_UI_CONTAINER_SELECTOR)).toBeVisible({
      timeout: 30_000,
    });

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

  test('CSRF token is filled in and Swagger UI is accessible with CSRF token', async ({
    operateHomePage,
    page,
  }) => {
    // setup login CSRF token
    await page.goto('/operate/login');
    const loginPage = new LoginPage(page);
    await loginPage.login(
      LOGIN_CREDENTIALS.username,
      LOGIN_CREDENTIALS.password,
    );
    await expect(operateHomePage.operateBanner).toBeVisible();

    // go to swagger UI
    await page.goto(SWAGGER_UI_URL);

    // Read browser cookies and find the CSRF cookie by name.
    const cookies = await page.context().cookies();
    const csrfCookie = cookies.find(
      (cookie) => cookie.name === CSRF_COOKIE_NAME,
    );

    // The CSRF cookie must be present and must not be the string "null"
    expect(csrfCookie).toBeDefined();
    expect(csrfCookie!.value).not.toBe('null');
    expect(csrfCookie!.value.length).toBeGreaterThan(0);

    // Wait for Swagger UI to fully initialize
    await expect(page.locator(SWAGGER_UI_CONTAINER_SELECTOR)).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.locator('.opblock-tag').first()).toBeVisible({
      timeout: 10_000,
    });

    // Find the POST /v2/process-definitions/search operation block and expand it
    const processDefinitionOperation = page
      .locator(PROCESS_DEFINITION_OPERATION_SELECTOR)
      .filter({hasText: PROCESS_DEFINITION_API_PATH})
      .first();
    await expect(processDefinitionOperation).toBeVisible({timeout: 10_000});
    await processDefinitionOperation.click();

    // Click "Try it out" so Swagger generates the request command preview
    const tryItOutButton = processDefinitionOperation.getByRole('button', {
      name: /try it out/i,
    });
    await expect(tryItOutButton).toBeVisible({timeout: 10_000});
    await tryItOutButton.click();

    // Trigger generation of the curl/response section without inspecting network requests.
    const executeButton = processDefinitionOperation.getByRole('button', {
      name: /execute/i,
    });
    await expect(executeButton).toBeVisible({timeout: 10_000});
    await executeButton.click();

    // Validate the generated curl command does not include a CSRF header.
    const curlCommand = processDefinitionOperation.locator(
      '.responses-wrapper .curl-command pre.curl code.language-bash',
    );
    await expect(curlCommand).toBeVisible({timeout: 10_000});
    const curlCommandText = await curlCommand.innerText();

    console.log(curlCommandText);
    expect(curlCommandText).toMatch(/x-csrf-token\s*:\s*/i);
    expect(curlCommandText).toMatch(/x-csrf-token\s*:\s*(?!null\b)\S+/i);
  });

  test('CSRF token is NOT filled in and Swagger UI is accessible', async ({
    page,
  }) => {
    // go to swagger UI
    await page.goto(SWAGGER_UI_URL);

    // Read browser cookies and find the CSRF cookie by name.
    const cookies = await page.context().cookies();
    console.log(cookies);
    const csrfCookie = cookies.find(
      (cookie) => cookie.name === CSRF_COOKIE_NAME,
    );

    // The CSRF cookie must NOT be present
    expect(csrfCookie).toBeUndefined();

    // Wait for Swagger UI to fully initialize
    await expect(page.locator(SWAGGER_UI_CONTAINER_SELECTOR)).toBeVisible({
      timeout: 10_000,
    });
    await expect(page.locator('.opblock-tag').first()).toBeVisible({
      timeout: 10_000,
    });

    // Find the POST /v2/process-definitions/search operation block and expand it
    const processDefinitionOperation = page
      .locator(PROCESS_DEFINITION_OPERATION_SELECTOR)
      .filter({hasText: PROCESS_DEFINITION_API_PATH})
      .first();
    await expect(processDefinitionOperation).toBeVisible({timeout: 10_000});
    await processDefinitionOperation.click();

    // Click "Try it out" so Swagger generates the request command preview
    const tryItOutButton = processDefinitionOperation.getByRole('button', {
      name: /try it out/i,
    });
    await expect(tryItOutButton).toBeVisible({timeout: 10_000});
    await tryItOutButton.click();

    // Trigger generation of the curl/response section without inspecting network requests.
    const executeButton = processDefinitionOperation.getByRole('button', {
      name: /execute/i,
    });
    await expect(executeButton).toBeVisible({timeout: 10_000});
    await executeButton.click();

    // Validate the generated curl command includes a non-null CSRF header.
    const curlCommand = processDefinitionOperation.locator(
      '.responses-wrapper .curl-command pre.curl code.language-bash',
    );
    await expect(curlCommand).toBeVisible({timeout: 10_000});
    const curlCommandText = await curlCommand.innerText();

    console.log(curlCommandText);
    expect(curlCommandText).not.toMatch(/x-csrf-token\s*:\s*/i);
  });
});
