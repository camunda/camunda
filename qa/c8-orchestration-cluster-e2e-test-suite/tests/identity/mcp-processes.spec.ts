/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from '@playwright/test';
import {test} from 'fixtures';
import {deploy} from 'utils/zeebeClient';
import {relativizePath, Paths} from 'utils/relativizePath';
import {navigateToApp} from '@pages/UtilitiesPage';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {LOGIN_CREDENTIALS} from 'utils/constants';
import {cleanupResources} from 'utils/resourceCleanup';
import {jsonHeaders, buildUrl} from 'utils/http';

const ALPHA_TOOL_NAME = 'alpha-tool-name';
const BRAVO_TOOL_NAME = 'bravo-tool-name';

test.describe('Identity MCP Processes', () => {
  const deployedResourceKeys: string[] = [];

  test.beforeAll(async ({request}) => {
    const alphaResult = await deploy(['./resources/mcpProcessAlpha.bpmn']);
    deployedResourceKeys.push(alphaResult.processes[0].processDefinitionKey);

    const bravoResult = await deploy(['./resources/mcpProcessBravo.bpmn']);
    deployedResourceKeys.push(bravoResult.processes[0].processDefinitionKey);

    await expect(async () => {
      const res = await request.post(
        buildUrl('/message-subscriptions/search'),
        {
          headers: jsonHeaders(),
          data: {filter: {toolName: {$exists: true}}},
        },
      );
      expect(res.status()).toBe(200);
      const json = await res.json();
      const toolNames = json.items.map(
        (item: {toolName: string}) => item.toolName,
      );
      expect(toolNames).toContain(ALPHA_TOOL_NAME);
      expect(toolNames).toContain(BRAVO_TOOL_NAME);
    }).toPass({
      intervals: [5_000, 10_000, 15_000],
      timeout: 30_000,
    });
  });

  test.beforeEach(async ({page, loginPage}) => {
    await navigateToApp(page, 'admin');
    await loginPage.login(
      LOGIN_CREDENTIALS.username,
      LOGIN_CREDENTIALS.password,
    );
    await expect(page).toHaveURL(relativizePath(Paths.users()));
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test.afterAll(async ({request}) => {
    await cleanupResources(request, deployedResourceKeys);
  });

  test('MCP Processes page shows table headers and entries', async ({
    page,
    identityMcpProcessesPage,
  }) => {
    await identityMcpProcessesPage.navigateToMcpProcesses();

    await test.step('Verify table headers are visible', async () => {
      await expect(identityMcpProcessesPage.mcpProcessesHeading).toBeVisible();

      await expect(
        page.getByRole('columnheader', {name: 'Tool Name'}),
      ).toBeVisible();
      await expect(
        page.getByRole('columnheader', {name: 'Tool Description'}),
      ).toBeVisible();
      await expect(
        page.getByRole('columnheader', {name: 'Process Name'}),
      ).toBeVisible();
      await expect(
        page.getByRole('columnheader', {name: 'Version'}),
      ).toBeVisible();
      await expect(
        page.getByRole('columnheader', {name: 'Tenant'}),
      ).toBeVisible();
    });

    await test.step('Verify MCP Process entries are visible', async () => {
      await expect(
        identityMcpProcessesPage.getRowByToolName(ALPHA_TOOL_NAME),
      ).toBeVisible();
      await expect(
        identityMcpProcessesPage.getRowByToolName(BRAVO_TOOL_NAME),
      ).toBeVisible();
    });
  });

  test('MCP Processes can be filtered by tool name', async ({
    identityMcpProcessesPage,
  }) => {
    await identityMcpProcessesPage.navigateToMcpProcesses();

    await test.step("Filter by tool name 'alpha'", async () => {
      await identityMcpProcessesPage.searchInput.fill('alpha');

      await expect(
        identityMcpProcessesPage.getRowByToolName(ALPHA_TOOL_NAME),
      ).toBeVisible();
      await expect(
        identityMcpProcessesPage.getRowByToolName(BRAVO_TOOL_NAME),
      ).toBeHidden();
    });

    await test.step('Clear search input', async () => {
      await identityMcpProcessesPage.searchInput.clear();

      await expect(
        identityMcpProcessesPage.getRowByToolName(ALPHA_TOOL_NAME),
      ).toBeVisible();
      await expect(
        identityMcpProcessesPage.getRowByToolName(BRAVO_TOOL_NAME),
      ).toBeVisible();
    });
  });

  test('MCP Processes can be sorted by tool name', async ({
    page,
    identityMcpProcessesPage,
  }) => {
    const rows = identityMcpProcessesPage.allMcpProcessRows;
    const toolNameHeader = page.getByRole('columnheader', {
      name: 'Tool Name',
    });
    await identityMcpProcessesPage.navigateToMcpProcesses();

    await test.step("Verify default sort is ascending by 'Tool Name'", async () => {
      await expect(rows.nth(1)).toContainText(ALPHA_TOOL_NAME);
      await expect(rows.nth(3)).toContainText(BRAVO_TOOL_NAME);
    });

    await test.step("Sort ASC by 'Tool Name'", async () => {
      // First click results in ASC order
      await toolNameHeader.click();

      await expect(rows.nth(1)).toContainText(ALPHA_TOOL_NAME);
      await expect(rows.nth(3)).toContainText(BRAVO_TOOL_NAME);
    });

    await test.step("Sort DESC by 'Tool Name'", async () => {
      // Second click results in DESC order
      await toolNameHeader.click();

      await expect(rows.nth(1)).toContainText(BRAVO_TOOL_NAME);
      await expect(rows.nth(3)).toContainText(ALPHA_TOOL_NAME);
    });
  });

  // TODO: Re-evaluate once extensionProperties are exported again
  test.skip('MCP Processes rows can be expanded to show more tool information', async ({
    identityMcpProcessesPage,
  }) => {
    await identityMcpProcessesPage.navigateToMcpProcesses();

    await test.step('Verify tool with all information', async () => {
      const details =
        identityMcpProcessesPage.getRowToolDetails(ALPHA_TOOL_NAME);

      await identityMcpProcessesPage.expandRowByToolName(ALPHA_TOOL_NAME);

      await expect(details.container).toBeVisible();

      await expect(details.purpose).toBeVisible();
      await expect(details.purpose).toHaveText(
        'Tool for executing an alpha process triggered by MCP.',
      );
      await expect(details.results).toBeVisible();
      await expect(details.results).toHaveText(
        'Alpha tool returns the result of the alpha process execution.',
      );
      await expect(details.whenToUse).toBeVisible();
      await expect(details.whenToUse).toHaveText(
        'Use the alpha tool when an alpha process needs to be triggered.',
      );
      await expect(details.whenNotToUse).toBeVisible();
      await expect(details.whenNotToUse).toHaveText(
        'Do not use the alpha tool for bravo processes.',
      );
    });

    await test.step('Verify tool with partial information', async () => {
      const details =
        identityMcpProcessesPage.getRowToolDetails(BRAVO_TOOL_NAME);
      await identityMcpProcessesPage.expandRowByToolName(BRAVO_TOOL_NAME);

      await expect(details.purpose).toBeVisible();
      await expect(details.purpose).toHaveText(
        'Tool for executing a bravo process triggered by MCP.',
      );
      await expect(details.results).toBeVisible();
      await expect(details.results).toHaveText(
        'Bravo tool returns the result of the bravo process execution.',
      );
      await expect(details.whenToUse).toBeVisible();
      await expect(details.whenToUse).toHaveText('No information provided.');
      await expect(details.whenNotToUse).toBeVisible();
      await expect(details.whenNotToUse).toHaveText('No information provided.');
    });
  });
});
