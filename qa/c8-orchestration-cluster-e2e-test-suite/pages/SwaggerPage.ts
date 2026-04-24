/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Page, Locator, expect, type APIRequestContext} from '@playwright/test';

import {credentials} from '../utils/http';

export class SwaggerPage {
  private readonly page: Page;
  private readonly csrfCookieName = 'X-CSRF-TOKEN';
  private readonly orchestrationClusterApiName = 'Orchestration Cluster API';
  readonly swaggerUiPath = '/swagger-ui/index.html';
  readonly apiDocsPath = '/v3/api-docs';
  readonly swaggerConfigPath = '/v3/api-docs/swagger-config';
  readonly processDefinitionSearchApiPath = '/v1/process-definitions/search';
  readonly swaggerUiContainer: Locator;
  readonly apiTags: Locator;
  readonly firstApiTag: Locator;

  constructor(page: Page) {
    this.page = page;
    this.swaggerUiContainer = page.locator(
      'section.swagger-ui.swagger-container',
    );
    this.apiTags = page.locator('.opblock-tag');
    this.firstApiTag = this.apiTags.first();
  }

  async gotoSwaggerUI(options?: Parameters<Page['goto']>[1]): Promise<void> {
    await this.page.goto(this.swaggerUiPath, options);
  }

  async expectLoaded(timeout: number = 30_000): Promise<void> {
    await expect(this.swaggerUiContainer).toBeVisible({timeout});
    await expect(this.firstApiTag).toBeVisible({timeout});
  }

  async getCsrfCookie() {
    const cookies = await this.page.context().cookies();

    return cookies.find((cookie) => cookie.name === this.csrfCookieName);
  }

  async getGroupedApiDocsUrl(request: APIRequestContext): Promise<string> {
    const swaggerConfigResponse = await request.get(this.swaggerConfigPath, {
      headers: {
        Accept: 'application/json',
        Authorization: `Basic ${credentials.accessToken}`,
      },
    });

    await expect(swaggerConfigResponse).toBeOK();

    const swaggerConfig = await swaggerConfigResponse.json();
    const orchestrationClusterApi = swaggerConfig.urls?.find(
      (entry: {name?: string; url?: string}) =>
        entry.name === this.orchestrationClusterApiName,
    );

    expect(orchestrationClusterApi?.url).toBeTruthy();

    return orchestrationClusterApi.url as string;
  }

  async openExecuteProcessDefinitionSearchDropdown(): Promise<void> {
    const operation = await this.getOperation(
      'post',
      this.processDefinitionSearchApiPath,
    );

    // noWaitAfter prevents Playwright from hanging on Swagger UI's hash-navigation
    // triggered by expanding the operation block.
    await operation.click({noWaitAfter: true});

    const tryItOutButton = operation.getByRole('button', {
      name: /try it out/i,
    });
    await expect(tryItOutButton).toBeVisible({timeout: 10_000});
    await tryItOutButton.click({noWaitAfter: true});

    const executeButton = operation.getByRole('button', {
      name: /execute/i,
    });
    await expect(executeButton).toBeVisible({timeout: 10_000});
    await executeButton.click();
  }

  async getProcessDefinitionSearchCurlCommandText(): Promise<string> {
    const curlCommand = (
      await this.getOperation('post', this.processDefinitionSearchApiPath)
    ).locator('.responses-wrapper .curl-command pre.curl code.language-bash');

    await expect(curlCommand).toBeVisible({timeout: 10_000});

    return curlCommand.innerText();
  }

  private async getOperation(
    method: string,
    apiPath: string,
  ): Promise<Locator> {
    const operation = this.page
      .locator(`.opblock.opblock-${method.toLowerCase()}`)
      .filter({hasText: apiPath})
      .first();

    await expect(operation).toBeVisible({timeout: 10_000});

    return operation;
  }
}
