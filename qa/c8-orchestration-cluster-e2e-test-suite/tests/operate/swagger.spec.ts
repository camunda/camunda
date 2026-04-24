/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from 'fixtures';
import {credentials} from '../../utils/http';
import {LOGIN_CREDENTIALS} from '../../utils/constants';

const collectRefs = (value: unknown): string[] => {
  if (Array.isArray(value)) {
    return value.flatMap(collectRefs);
  }

  if (value && typeof value === 'object') {
    return Object.entries(value).flatMap(([key, nestedValue]) =>
      key === '$ref' && typeof nestedValue === 'string'
        ? [nestedValue, ...collectRefs(nestedValue)]
        : collectRefs(nestedValue),
    );
  }

  return [];
};

test.beforeEach(async ({context}) => {
  // Ensure this scenario is fully unauthenticated even if prior tests logged in.
  await context.clearCookies();
});

test.describe('Swagger UI Tests', () => {
  test('Swagger UI is accessible and presents the API list', async ({
    swaggerPage,
  }) => {
    await swaggerPage.gotoSwaggerUI();

    // Verify the main Swagger UI container is rendered
    await expect(swaggerPage.swaggerUiContainer).toBeVisible({
      timeout: 30_000,
    });

    // Verify that at least one API section/tag is listed
    await expect(swaggerPage.firstApiTag).toBeVisible({timeout: 30_000});
  });

  test('Grouped OpenAPI spec returns valid JSON without external file refs', async ({
    request,
    swaggerPage,
  }) => {
    // given
    const groupedApiDocsUrl = await swaggerPage.getGroupedApiDocsUrl(request);

    // when
    const response = await request.get(
      `${credentials.baseUrl}${groupedApiDocsUrl}`,
      {
        headers: {
          Accept: 'application/json',
          Authorization: `Basic ${credentials.accessToken}`,
        },
      },
    );

    // then
    expect(response.status()).toBe(200);

    const contentType = response.headers()['content-type'];
    expect(contentType).toContain('application/json');

    const body = await response.json();

    expect(typeof body).toBe('object');
    expect(body).toHaveProperty('openapi');
    expect(body).toHaveProperty('info');
    expect(body).toHaveProperty('paths');
    expect(groupedApiDocsUrl).not.toBe(swaggerPage.apiDocsPath);

    const refs = collectRefs(body);
    const externalFileRefs = refs.filter((ref) => ref.includes('.yaml#'));

    expect(externalFileRefs).toEqual([]);
  });

  test('CSRF token is filled in and Swagger UI is accessible with CSRF token', async ({
    loginPage,
    operateHomePage,
    page,
    swaggerPage,
  }) => {
    // setup login CSRF token
    await page.goto('/operate/login');
    await loginPage.login(
      LOGIN_CREDENTIALS.username,
      LOGIN_CREDENTIALS.password,
    );
    await expect(operateHomePage.operateBanner).toBeVisible();

    // go to swagger UI
    await swaggerPage.gotoSwaggerUI();

    // Read browser cookies and find the CSRF cookie by name.
    const csrfCookie = await swaggerPage.getCsrfCookie();

    // The CSRF cookie must be present and must not be the string "null"
    expect(csrfCookie).toBeDefined();
    expect(csrfCookie!.value).not.toBe('null');
    expect(csrfCookie!.value.length).toBeGreaterThan(0);

    // Wait for Swagger UI to fully initialize
    await swaggerPage.expectLoaded(10_000);
    await swaggerPage.openExecuteProcessDefinitionSearchDropdown();
    const curlCommandText =
      await swaggerPage.getProcessDefinitionSearchCurlCommandText();
    expect(curlCommandText).toMatch(/x-csrf-token\s*:\s*/i);
    expect(curlCommandText).toMatch(/x-csrf-token\s*:\s*(?!null\b)\S+/i);
  });

  test('CSRF token is NOT filled in and Swagger UI is accessible', async ({
    swaggerPage,
  }) => {
    // go to swagger UI
    await swaggerPage.gotoSwaggerUI();

    // Read browser cookies and find the CSRF cookie by name.
    const csrfCookie = await swaggerPage.getCsrfCookie();

    // The CSRF cookie must NOT be present
    expect(csrfCookie).toBeUndefined();

    // Wait for Swagger UI to fully initialize
    await swaggerPage.expectLoaded(10_000);
    await swaggerPage.openExecuteProcessDefinitionSearchDropdown();
    const curlCommandText =
      await swaggerPage.getProcessDefinitionSearchCurlCommandText();
    expect(curlCommandText).not.toMatch(/x-csrf-token\s*:\s*/i);
  });
});
