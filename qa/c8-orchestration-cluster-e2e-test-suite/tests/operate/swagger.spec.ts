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

const SWAGGER_UI_URL = '/swagger-ui/index.html';
const API_DOCS_URL = '/v3/api-docs';
const SWAGGER_UI_CONTAINER_SELECTOR = 'section.swagger-ui.swagger-container';

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
});
