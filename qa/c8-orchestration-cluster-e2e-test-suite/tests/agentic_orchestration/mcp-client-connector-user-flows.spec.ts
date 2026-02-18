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
    './resources/agentic_orchestration/mcp_remote_client_operations.bpmn',
  ]);
  await createInstances('mcp_remote_client', 1, 1);
  await sleep(2000);
});

test.describe('MCP Client connector tests', () => {
  test.beforeEach(async ({page, loginPage, operateHomePage}) => {
    await navigateToApp(page, 'operate');
    await loginPage.login('demo', 'demo');
    await expect(operateHomePage.operateBanner).toBeVisible();
  });

  test.afterEach(async ({page}, testInfo) => {
    await captureScreenshot(page, testInfo);
    await captureFailureVideo(page, testInfo);
  });

  test('As an user I can invoke the MCP Client connector and use all its operations', async ({
    operateHomePage,
    operateProcessesPage,
    operateFiltersPanelPage,
    operateProcessInstancePage,
  }) => {
    test.slow();

    await test.step('Navigate to completed process instance', async () => {
      await operateHomePage.clickProcessesTab();
      await operateFiltersPanelPage.clickCompletedInstancesCheckbox();
      await operateFiltersPanelPage.selectProcess('mcp_remote_client');
      await sleep(100);
      await operateProcessesPage.clickProcessInstanceLink();
      await expect(operateProcessInstancePage.completedIcon).toBeVisible({
        timeout: 60000,
      });
    });

    await test.step('Verify listToolsResult variable contains tools', async () => {
      await operateProcessInstancePage.verifyVariableJsonContent(
        'listToolsResult',
        (value) => {
          expect(value.toolDefinitions).toBeDefined();
          expect(value.toolDefinitions.length).toBeGreaterThan(0);
          const greetTool = value.toolDefinitions.find(
            (tool: {name: string}) => tool.name === 'greet',
          );
          expect(greetTool).toBeDefined();
          expect(greetTool.description).toBe('Return a nice greeting message');
        },
      );
    });

    await test.step('Verify callToolResult variable from greet tool call', async () => {
      await operateProcessInstancePage.verifyVariableJsonContent(
        'callToolResult',
        (value) => {
          expect(value.name).toBe('greet');
          expect(value.isError).toBe(false);
          expect(value.content).toBeDefined();
          expect(value.content[0].type).toBe('text');
          expect(value.content[0].text).toContain('Hello, John');
        },
      );
    });

    await test.step('Verify listResourcesResult variable contains resources', async () => {
      await operateProcessInstancePage.verifyVariableJsonContent(
        'listResourcesResult',
        (value) => {
          expect(value.resources).toBeDefined();
          expect(value.resources.length).toBeGreaterThan(0);
          const jokesResource = value.resources.find(
            (resource: {uri: string}) =>
              resource.uri === 'file://jokes-guide.md',
          );
          expect(jokesResource).toBeDefined();
          expect(jokesResource.name).toBe('Jokes Guideline');
          expect(jokesResource.mimeType).toBe('text/markdown');
        },
      );
    });

    await test.step('Verify listResourcesTemplateResult variable contains resource templates', async () => {
      await operateProcessInstancePage.verifyVariableJsonContent(
        'listResourcesTemplateResult',
        (value) => {
          expect(value.resourceTemplates).toBeDefined();
          expect(value.resourceTemplates).toEqual([]);
        },
      );
    });

    await test.step('Verify readResourceResult variable from jokes-guide.md', async () => {
      await operateProcessInstancePage.verifyVariableJsonContent(
        'readResourceResult',
        (value) => {
          expect(value.contents).toBeDefined();
          expect(value.contents.length).toBeGreaterThan(0);
          expect(value.contents[0].uri).toBe('file://jokes-guide.md');
          expect(value.contents[0].mimeType).toBe('text/markdown');
          expect(value.contents[0].text).toContain('How to Write Jokes');
        },
      );
    });

    await test.step('Verify listPromptsResult variable contains prompts', async () => {
      await operateProcessInstancePage.verifyVariableJsonContent(
        'listPromptsResult',
        (value) => {
          expect(value.promptDescriptions).toBeDefined();
          expect(value.promptDescriptions.length).toBeGreaterThan(0);
          const greetingPrompt = value.promptDescriptions.find(
            (prompt: {name: string}) => prompt.name === 'get-greeting',
          );
          expect(greetingPrompt).toBeDefined();
          expect(greetingPrompt.description).toContain('personalized greeting');
        },
      );
    });

    await test.step('Verify getPromptResult variable from get-greeting prompt', async () => {
      await operateProcessInstancePage.verifyVariableJsonContent(
        'getPromptResult',
        (value) => {
          expect(value.description).toBe('Greeting Prompt');
          expect(value.messages).toBeDefined();
          expect(value.messages.length).toBeGreaterThan(0);
          expect(value.messages[0].role).toBe('user');
          expect(value.messages[0].content.text).toContain('John');
          expect(value.messages[0].content.text).toContain('greet tool');
        },
      );
    });
  });
});
