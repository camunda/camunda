/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test} from '@fixtures';
import {expect} from '@playwright/test';
import {navigateToApp} from '@pages/UtilitiesPage';
import {validateMcpServerHealth} from 'utils/mcpServerHelpers';
import {deploy, createInstances} from 'utils/zeebeClient';
import {sleep} from 'utils/sleep';
import {captureScreenshot, captureFailureVideo} from '@setup';
import {assertProcessVariableContainsText} from 'utils/operateVariableHelpers';

test.beforeAll(async () => {
  console.log('Verifying MCP server is healthy...');
  await validateMcpServerHealth();
  console.log('✓ MCP server is healthy');

  console.log('Deploying MCP BPMN process...');
  await deploy(['./resources/mcp_server/mcp_server_list_tools.bpmn']);
  console.log('✓ BPMN process deployed');

  console.log('Creating process instances...');
  await createInstances('mcp_remote_client', 1, 1);
  console.log('✓ Process instances created');

  await sleep(5000);
});

test.describe('MCP Test Server User Flows', () => {
  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('MCP server returns list of tools from process', async ({
    page,
    operateDashboardPage,
    operateProcessesPage,
    operateProcessInstancePage,
  }) => {
    test.slow();

    await test.step('Navigate to Operate', async () => {
      await navigateToApp(page, 'operate');
    });

    await test.step('User can access the completed process on operate', async () => {
      console.log('Navigating to Operate processes tab...');
      await operateDashboardPage.gotoProcesses();

      console.log('Filtering for completed processes...');
      await operateProcessesPage.selectCompletedInstances();

      console.log('Opening MCP process instance...');
      await operateProcessesPage.getNthProcessInstanceLink('mcp_remote_client', 0).click();

      console.log('Verifying process completion...');
      await expect(operateProcessInstancePage.completedIcon).toBeVisible({
        timeout: 60000,
      });
      console.log('✓ Process completed successfully');
    });

    await test.step('Verify MCP tools are returned in the process result', async () => {
      console.log('Checking for toolCallResult variable...');
      await assertProcessVariableContainsText(page, 'toolCallResult', 'add');
      await assertProcessVariableContainsText(page, 'toolCallResult', 'greet');
      await assertProcessVariableContainsText(page, 'toolCallResult', 'echo');
      console.log('✓ MCP tools list found in process result');
    });
  });
});
