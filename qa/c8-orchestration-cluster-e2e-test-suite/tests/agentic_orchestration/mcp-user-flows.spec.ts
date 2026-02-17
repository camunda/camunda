/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from 'fixtures';
import {expect} from '@playwright/test';
import {navigateToApp} from '@pages/UtilitiesPage';
import {validateMcpServerHealth} from 'utils/mcpServerHelpers';
import {validateConnectorsHealth} from 'utils/connectorsRuntimeHelpers';
import {deploy, createInstances} from 'utils/zeebeClient';
import {sleep} from 'utils/sleep';
import {captureScreenshot, captureFailureVideo} from '@setup';

test.beforeAll(async () => {
  await validateMcpServerHealth();
  await validateConnectorsHealth();
  await deploy([
    './resources/agentic_orchestration/mcp_server_list_tools.bpmn',
  ]);
  await createInstances('mcp_remote_client', 1, 1);
  await sleep(10000);
});

test.describe('MCP Server Integration', () => {
  test.beforeEach(async ({page, loginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('Verify MCP server returns list of tools', async ({
    operateHomePage,
    operateProcessesPage,
    operateProcessInstancePage,
  }) => {
    test.slow();

    await test.step('Navigate to completed process instance', async () => {
      await operateHomePage.clickProcessesTab();
      await operateProcessesPage.clickProcessCompletedCheckbox();
      await operateProcessesPage.filterByProcessName('mcp_remote_client');
      await sleep(100);
      await operateProcessesPage.clickProcessInstanceLink();
      await expect(operateProcessInstancePage.completedIcon).toBeVisible({
        timeout: 60000,
      });
    });

    await test.step('Verify toolCallResult variable contains expected tools', async () => {
      const toolCallResult =
        operateProcessInstancePage.existingVariableByName('toolCallResult');
      await expect(toolCallResult.value).toContainText('add', {timeout: 30000});
      await expect(toolCallResult.value).toContainText('greet');
      await expect(toolCallResult.value).toContainText('echo');
    });
  });
});
