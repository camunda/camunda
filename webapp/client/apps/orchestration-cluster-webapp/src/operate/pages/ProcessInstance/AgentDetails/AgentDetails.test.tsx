/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import i18n from 'i18next';
import {afterEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {render} from 'vitest-browser-react';
import type {AgentInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {createAgentInstance} from '#/shared-test-modules/api-mocks/agent-instances';
import {AgentDetails} from './AgentDetails';

function renderAgentDetails({
	agentInstances = [createAgentInstance()],
	totalAgentsCount = agentInstances.length,
	hasMoreTotalItems = false,
	isError = false,
}: {
	agentInstances?: AgentInstance[];
	totalAgentsCount?: number;
	hasMoreTotalItems?: boolean;
	isError?: boolean;
} = {}) {
	return render(
		<AgentDetails
			agentInstances={agentInstances}
			totalAgentsCount={totalAgentsCount}
			hasMoreTotalItems={hasMoreTotalItems}
			isError={isError}
		/>,
	);
}

describe('<AgentDetails />', () => {
	afterEach(async () => {
		await i18n.changeLanguage('en');
	});

	it.for([
		['UNKNOWN', 'Unknown'],
		['INITIALIZING', 'Initializing'],
		['THINKING', 'Thinking'],
		['TOOL_CALLING', 'Calling tools'],
		['TOOL_DISCOVERY', 'Discovering tools'],
		['IDLE', 'Idle'],
		['COMPLETED', 'Completed'],
	] as const)('should render the %s status', async ([status, label]) => {
		const screen = await renderAgentDetails({agentInstances: [createAgentInstance({status})]});

		await expect.element(screen.getByRole('heading', {name: 'Agent Instance'})).toBeVisible();
		await expect.element(screen.getByText(`Status: ${label}`)).toBeVisible();
		await expect.element(screen.getByRole('button', {name: `Status: ${label}`})).not.toBeInTheDocument();
	});

	it('should render an error hint when the agent instances failed to load', async () => {
		const screen = await renderAgentDetails({isError: true});

		await expect.element(screen.getByRole('heading', {name: 'Agent Instance'})).toBeVisible();
		await expect.element(screen.getByText('Unable to load agent information.')).toBeVisible();
		await expect.element(screen.getByText(/^Status:/)).not.toBeInTheDocument();
	});

	it('should render the agent instance key without a selector when only one agent exists', async () => {
		const screen = await renderAgentDetails({agentInstances: [createAgentInstance({agentInstanceKey: 'agent-42'})]});

		await expect.element(screen.getByText('agent-42')).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: 'Current AI agent'})).not.toBeInTheDocument();
	});

	it('should default the selector to the first agent and switch on selection change', async () => {
		const screen = await renderAgentDetails({
			agentInstances: [
				createAgentInstance({agentInstanceKey: '1', status: 'THINKING'}),
				createAgentInstance({agentInstanceKey: '2', status: 'COMPLETED'}),
			],
		});

		await expect.element(screen.getByText('Status: Thinking')).toBeVisible();

		await userEvent.click(screen.getByRole('combobox', {name: 'Current AI agent'}));
		await expect.element(screen.getByRole('option', {name: '1 - Thinking'})).toBeVisible();
		await userEvent.click(screen.getByRole('option', {name: '2 - Completed'}));

		await expect.element(screen.getByText('Status: Completed')).toBeVisible();
		await expect.element(screen.getByText('Status: Thinking')).not.toBeInTheDocument();
	});

	it('should fall back to the first agent when the selected agent is no longer listed', async () => {
		const screen = await renderAgentDetails({
			agentInstances: [
				createAgentInstance({agentInstanceKey: '1', status: 'THINKING'}),
				createAgentInstance({agentInstanceKey: '2', status: 'COMPLETED'}),
			],
		});
		await userEvent.click(screen.getByRole('combobox', {name: 'Current AI agent'}));
		await userEvent.click(screen.getByRole('option', {name: '2 - Completed'}));
		await expect.element(screen.getByText('Status: Completed')).toBeVisible();

		await screen.rerender(
			<AgentDetails
				agentInstances={[
					createAgentInstance({agentInstanceKey: '3', status: 'IDLE'}),
					createAgentInstance({agentInstanceKey: '4', status: 'THINKING'}),
				]}
				totalAgentsCount={2}
				hasMoreTotalItems={false}
				isError={false}
			/>,
		);

		await expect.element(screen.getByText('Status: Idle')).toBeVisible();
		await expect.element(screen.getByText('Unable to load agent information.')).not.toBeInTheDocument();
	});

	it('should hint at agents that are not shown', async () => {
		const screen = await renderAgentDetails({
			agentInstances: [
				createAgentInstance({agentInstanceKey: '1', status: 'THINKING'}),
				createAgentInstance({agentInstanceKey: '2', status: 'COMPLETED'}),
			],
			totalAgentsCount: 10,
			hasMoreTotalItems: true,
		});

		await userEvent.click(screen.getByRole('combobox', {name: 'Current AI agent'}));

		await expect.element(screen.getByRole('option', {name: '2 - Completed'})).toBeVisible();
		await expect.element(screen.getByRole('option', {name: '8+ AI agents not shown'})).toBeVisible();
	});

	it('should render the usage summary and metrics', async () => {
		const screen = await renderAgentDetails({
			agentInstances: [
				createAgentInstance({
					metrics: {
						inputTokens: 100,
						outputTokens: 50,
						reasoningTokenCount: 10,
						cacheCreationTokenCount: 20,
						cacheReadTokenCount: 30,
						modelCalls: 4,
						toolCalls: 3,
					},
					limits: {maxModelCalls: 8, maxToolCalls: -1, maxTokens: 400},
				}),
			],
		});
		const usage = screen.getByRole('button', {name: 'Usage'});

		await expect.element(usage).toMatchTextContent('4 model calls');
		await expect.element(usage).toMatchTextContent(/200\stokens/);

		await userEvent.click(usage);

		const modelCalls = screen.getByRole('article', {name: 'Model Calls'});
		const tokensUsed = screen.getByRole('article', {name: 'Tokens Used'});
		const toolsCalled = screen.getByRole('article', {name: 'Tools Called'});
		await expect.element(modelCalls.getByRole('meter')).toHaveAttribute('aria-valuetext', '4 of 8 limit (50%)');
		await expect.element(tokensUsed.getByRole('meter')).toHaveAttribute('aria-valuetext', '200 of 400 limit (50%)');
		await expect.element(toolsCalled).toBeVisible();
		await expect.element(toolsCalled.getByRole('meter')).not.toBeInTheDocument();
	});

	it('should format numbers in the selected application language', async () => {
		await i18n.changeLanguage('de');
		const screen = await renderAgentDetails({
			agentInstances: [
				createAgentInstance({
					metrics: {
						inputTokens: 1000,
						outputTokens: 500,
						reasoningTokenCount: 0,
						cacheCreationTokenCount: 0,
						cacheReadTokenCount: 0,
						modelCalls: 1234,
						toolCalls: 0,
					},
					limits: {maxModelCalls: 2000, maxToolCalls: -1, maxTokens: -1},
				}),
			],
		});
		const usage = screen.getByRole('button', {name: 'Nutzung'});

		await expect.element(usage).toMatchTextContent('1.234 Modellaufrufe');
		await expect.element(usage).toMatchTextContent(/1\.500\sTokens/);

		await userEvent.click(usage);

		const modelCalls = screen.getByRole('article', {name: 'Modellaufrufe'});
		await expect.element(modelCalls).toMatchTextContent('1.234');
		await expect.element(modelCalls).toMatchTextContent('von 2.000 Limit');
		await expect
			.element(modelCalls.getByRole('meter'))
			.toHaveAttribute('aria-valuetext', '1.234 von 2.000 Limit (62 %)');
	});

	it('should render the available tools sorted by name', async () => {
		const screen = await renderAgentDetails({
			agentInstances: [
				createAgentInstance({
					tools: [
						{name: 'search', description: 'Searches the web', elementId: null},
						{name: 'calculator', description: null, elementId: null},
					],
				}),
			],
		});

		await userEvent.click(screen.getByRole('button', {name: 'Available tools'}));

		await expect.element(screen.getByRole('listitem', {name: 'search'})).toMatchTextContent('Searches the web');
		await expect.element(screen.getByRole('listitem', {name: 'calculator'})).toHaveTextContent('calculator');
		await expect
			.element(screen.getByTestId('agent-available-tools-section'))
			.toHaveTextContent('Available toolscalculatorsearchSearches the web');
	});

	it('should render a hint when no tools are available', async () => {
		const screen = await renderAgentDetails();

		await userEvent.click(screen.getByRole('button', {name: 'Available tools'}));

		await expect.element(screen.getByText('No tools available for the AI agent.')).toBeVisible();
	});

	it('should render the model provider, model and agent definition key', async () => {
		const screen = await renderAgentDetails({
			agentInstances: [
				createAgentInstance({
					agentDefinitionKey: 'definition-7',
					definition: {provider: 'openai', model: 'gpt-4o', systemPrompt: []},
				}),
			],
		});

		await userEvent.click(screen.getByRole('button', {name: 'Model & Agent definition'}));

		await expect.element(screen.getByText('Provider: openai')).toBeVisible();
		await expect.element(screen.getByText('Model: gpt-4o')).toBeVisible();
		await expect.element(screen.getByText('Agent definition key: definition-7')).toBeVisible();
	});
});
