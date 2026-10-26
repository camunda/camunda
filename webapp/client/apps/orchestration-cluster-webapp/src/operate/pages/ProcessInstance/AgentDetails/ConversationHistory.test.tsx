/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {render} from 'vitest-browser-react';
import {z} from 'zod';
import type {AgentInstanceHistoryItem, AgentInstanceStatus} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {
	createAgentInstanceHistoryItem,
	createQueryAgentInstanceHistoryResponse,
} from '#/shared-test-modules/api-mocks/agent-instances';
import {createDocumentReference} from '#/shared-test-modules/api-mocks/documents';
import {mockQueryAgentInstanceHistoryEndpoint} from '#/shared-test-modules/mock-handlers';
import {ConversationHistory} from './ConversationHistory';

function historyRequestSchema({
	order = 'DESC',
	after,
	elementInstanceKey,
}: {order?: 'ASC' | 'DESC'; after?: string; elementInstanceKey?: string} = {}) {
	return z.object({
		sort: z.tuple([
			z.object({field: z.literal('producedAt'), order: z.literal(order)}),
			z.object({field: z.literal('historyItemKey'), order: z.literal(order)}),
		]),
		filter: z.object({
			role: z.object({$in: z.tuple([z.literal('ASSISTANT'), z.literal('USER'), z.literal('TOOL_RESULT')])}),
			commitStatus: z.literal('COMMITTED'),
			elementInstanceKey: elementInstanceKey === undefined ? z.undefined().optional() : z.literal(elementInstanceKey),
		}),
		page: z.object({limit: z.literal(100), after: after === undefined ? z.undefined().optional() : z.literal(after)}),
	});
}

function mockHistory(
	items: AgentInstanceHistoryItem[],
	{
		totalItems,
		endCursor,
		hasMoreTotalItems,
		...request
	}: Parameters<typeof historyRequestSchema>[0] & {
		totalItems?: number;
		endCursor?: string;
		hasMoreTotalItems?: boolean;
	} = {},
) {
	return mockQueryAgentInstanceHistoryEndpoint({
		schema: historyRequestSchema(request),
		successResponse: HttpResponse.json(
			createQueryAgentInstanceHistoryResponse(items, {totalItems, endCursor, hasMoreTotalItems}),
		),
		failureResponse: new HttpResponse(null, {status: 400}),
	});
}

function textItem(historyItemKey: string, role: 'USER' | 'ASSISTANT', text: string, loopIteration = 1) {
	return createAgentInstanceHistoryItem({historyItemKey, role, loopIteration, content: [{contentType: 'TEXT', text}]});
}

function renderHistory({
	agentInstanceStatus = 'COMPLETED',
	selectedElementInstanceKey = null,
	agentsElementInstanceKeys = [],
}: {
	agentInstanceStatus?: AgentInstanceStatus;
	selectedElementInstanceKey?: string | null;
	agentsElementInstanceKeys?: string[];
} = {}) {
	const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
	const Wrapper = ({children}: {children: React.ReactNode}) => (
		<QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
	);

	return render(
		<ConversationHistory
			agentInstanceKey="agent-1"
			agentInstanceStatus={agentInstanceStatus}
			availableTools={[{name: 'searchFlights', description: 'Searches available flights', elementId: null}]}
			isVisible
			selectedElementInstanceKey={selectedElementInstanceKey}
			agentsElementInstanceKeys={agentsElementInstanceKeys}
		/>,
		{wrapper: Wrapper},
	);
}

describe('<ConversationHistory />', () => {
	it('should render an error message when loading fails', async ({worker}) => {
		worker.use(mockQueryAgentInstanceHistoryEndpoint({successResponse: new HttpResponse(null, {status: 500})}));
		const screen = await renderHistory();

		await expect.element(screen.getByText('Failed to load conversation history.')).toBeVisible();
	});

	it('should show a hint when there are no conversation messages', async ({worker}) => {
		worker.use(mockHistory([]));
		const screen = await renderHistory();

		await expect.element(screen.getByText('No conversation with this agent instance found.')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Latest first'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Scoped conversation'})).not.toBeInTheDocument();
	});

	it('should render every message variant', async ({worker}) => {
		worker.use(
			mockHistory([
				createAgentInstanceHistoryItem({
					historyItemKey: 'configuration',
					role: 'CONFIGURATION',
					content: [{contentType: 'TEXT', text: 'Configuration entry'}],
				}),
				createAgentInstanceHistoryItem({
					historyItemKey: 'tool-result',
					role: 'TOOL_RESULT',
					toolCalls: [{toolCallId: 'call-1', toolName: 'searchFlights', elementId: null, arguments: {to: 'BER'}}],
					content: [{contentType: 'OBJECT', object: {flights: 2}}],
				}),
				createAgentInstanceHistoryItem({
					historyItemKey: 'assistant',
					role: 'ASSISTANT',
					content: [
						{contentType: 'TEXT', text: 'Let me check.'},
						{contentType: 'OBJECT', object: {plan: 'search'}},
						{
							contentType: 'DOCUMENT',
							documentReference: createDocumentReference({metadata: {fileName: 'itinerary.pdf'}}),
						},
					],
					toolCalls: [{toolCallId: 'call-1', toolName: 'searchFlights', elementId: null, arguments: {to: 'BER'}}],
					metrics: {
						inputTokens: 100,
						outputTokens: 20,
						reasoningTokenCount: null,
						cacheCreationTokenCount: null,
						cacheReadTokenCount: null,
						durationMs: 1500,
					},
				}),
				textItem('user', 'USER', 'Find me a flight'),
			]),
		);
		const screen = await renderHistory();

		const toolResult = screen.getByRole('article', {name: 'Result for "searchFlights" tool call'});
		await expect.element(toolResult).toMatchTextContent('{"flights":2}');

		const assistant = screen.getByRole('article', {name: 'Assistant message'});
		await expect.element(assistant).toMatchTextContent('Let me check.');
		await expect.element(assistant).toMatchTextContent(/"plan": "search"/);
		await expect.element(assistant.getByRole('list', {name: 'Documents'})).toHaveTextContent('itinerary.pdf');
		await expect.element(assistant.getByRole('list', {name: 'Tool calls'})).toHaveTextContent('searchFlights');
		await expect.element(assistant.getByText('120 tokens')).toBeVisible();
		await expect.element(assistant.getByText('1.50s')).toBeVisible();

		await expect.element(screen.getByRole('article', {name: 'User message'})).toMatchTextContent('Find me a flight');
		await expect.element(screen.getByText('Configuration entry')).not.toBeInTheDocument();
	});

	it('should toggle the sort order and move the iteration markers accordingly', async ({worker}) => {
		worker.use(
			mockHistory([textItem('2', 'ASSISTANT', 'Second answer', 2), textItem('1', 'USER', 'First question', 1)]),
		);
		const screen = await renderHistory();

		await expect.element(screen.getByText('Second answer')).toBeVisible();
		await expect
			.element(screen.container)
			.toMatchTextContent(/Second answer.*2\.\siteration.*First question.*1\.\siteration/);

		worker.use(
			mockHistory([textItem('1', 'USER', 'First question', 1), textItem('2', 'ASSISTANT', 'Second answer', 2)], {
				order: 'ASC',
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Latest first'}));

		await expect.element(screen.getByRole('button', {name: 'Oldest first'})).toBeVisible();
		await expect
			.element(screen.container)
			.toMatchTextContent(/1\.\siteration.*First question.*2\.\siteration.*Second answer/);
	});

	it('should keep the toggles available when switching the sort order fails', async ({worker}) => {
		worker.use(mockHistory([textItem('1', 'USER', 'Hello')]));
		const screen = await renderHistory();
		await expect.element(screen.getByText('Hello')).toBeVisible();

		worker.use(mockQueryAgentInstanceHistoryEndpoint({successResponse: new HttpResponse(null, {status: 500})}));
		await userEvent.click(screen.getByRole('button', {name: 'Latest first'}));

		await expect.element(screen.getByText('Failed to load conversation history.')).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Oldest first'}));

		await expect.element(screen.getByText('Hello')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Latest first'})).toBeVisible();
	});

	it('should load more messages on demand', async ({worker}) => {
		worker.use(mockHistory([textItem('1', 'USER', 'First page message')], {totalItems: 2, endCursor: 'cursor-1'}));
		const screen = await renderHistory();
		await expect.element(screen.getByText('First page message')).toBeVisible();

		worker.use(
			mockHistory([textItem('2', 'ASSISTANT', 'Second page message')], {
				after: 'cursor-1',
				totalItems: 2,
				endCursor: 'cursor-2',
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Show more'}));

		await expect.element(screen.getByText('Second page message')).toBeVisible();
		await expect.element(screen.getByText('First page message')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Show more'})).not.toBeInTheDocument();
	});

	it('should keep loading more messages while the total is only a lower bound', async ({worker}) => {
		worker.use(
			mockHistory([textItem('1', 'USER', 'First page message')], {
				totalItems: 1,
				hasMoreTotalItems: true,
				endCursor: 'cursor-1',
			}),
		);
		const screen = await renderHistory();
		await expect.element(screen.getByText('First page message')).toBeVisible();

		worker.use(
			mockHistory([textItem('2', 'ASSISTANT', 'Second page message')], {
				after: 'cursor-1',
				totalItems: 2,
				endCursor: 'cursor-2',
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Show more'}));

		await expect.element(screen.getByText('Second page message')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Show more'})).not.toBeInTheDocument();
	});

	it('should show each message once when a loaded page overlaps the previous one', async ({worker}) => {
		worker.use(mockHistory([textItem('2', 'ASSISTANT', 'Newest message')], {totalItems: 3, endCursor: 'cursor-1'}));
		const screen = await renderHistory();
		await expect.element(screen.getByText('Newest message')).toBeVisible();

		worker.use(
			mockHistory([textItem('2', 'ASSISTANT', 'Newest message'), textItem('1', 'USER', 'Oldest message')], {
				after: 'cursor-1',
				totalItems: 3,
				endCursor: 'cursor-2',
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Show more'}));

		await expect.element(screen.getByText('Oldest message')).toBeVisible();
		await expect.element(screen.getByText('Newest message')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Show more'})).toBeVisible();
	});

	it('should keep the loaded messages and allow a retry when loading more fails', async ({worker}) => {
		worker.use(mockHistory([textItem('1', 'USER', 'First page message')], {totalItems: 2, endCursor: 'cursor-1'}));
		const screen = await renderHistory();
		await expect.element(screen.getByText('First page message')).toBeVisible();

		worker.use(mockQueryAgentInstanceHistoryEndpoint({successResponse: new HttpResponse(null, {status: 500})}));
		await userEvent.click(screen.getByRole('button', {name: 'Show more'}));

		await expect.element(screen.getByText('Failed to load conversation history.')).toBeVisible();
		await expect.element(screen.getByText('First page message')).toBeVisible();

		worker.use(
			mockHistory([textItem('2', 'ASSISTANT', 'Second page message')], {
				after: 'cursor-1',
				totalItems: 2,
				endCursor: 'cursor-2',
			}),
		);
		await userEvent.click(screen.getByRole('button', {name: 'Show more'}));

		await expect.element(screen.getByText('Second page message')).toBeVisible();
		await expect.element(screen.getByText('Failed to load conversation history.')).not.toBeInTheDocument();
	});

	it('should not offer a scope toggle when the agent was activated once', async ({worker}) => {
		worker.use(mockHistory([textItem('1', 'USER', 'Hello')]));
		const screen = await renderHistory({
			selectedElementInstanceKey: 'element-instance-1',
			agentsElementInstanceKeys: ['element-instance-1'],
		});

		await expect.element(screen.getByText('Hello')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Scoped conversation'})).not.toBeInTheDocument();
	});

	it('should toggle between the scoped and the whole conversation', async ({worker}) => {
		worker.use(mockHistory([], {elementInstanceKey: 'element-instance-2'}));
		const screen = await renderHistory({
			selectedElementInstanceKey: 'element-instance-2',
			agentsElementInstanceKeys: ['element-instance-1', 'element-instance-2'],
		});

		await expect.element(screen.getByText('No scoped conversation with the agent instance found.')).toBeVisible();

		worker.use(mockHistory([textItem('1', 'USER', 'Earlier activation')]));
		await userEvent.click(screen.getByRole('button', {name: 'Scoped conversation'}));

		await expect.element(screen.getByRole('button', {name: 'Whole conversation'})).toBeVisible();
		await expect.element(screen.getByText('Earlier activation')).toBeVisible();
	});

	it('should refetch the history on reopening when the agent status changed while it was closed', async ({worker}) => {
		const historyAt = (agentInstanceStatus: AgentInstanceStatus, isVisible: boolean) => (
			<ConversationHistory
				agentInstanceKey="agent-1"
				agentInstanceStatus={agentInstanceStatus}
				availableTools={[]}
				isVisible={isVisible}
				selectedElementInstanceKey={null}
				agentsElementInstanceKeys={[]}
			/>
		);
		worker.use(mockHistory([textItem('1', 'ASSISTANT', 'Working on it')]));
		const screen = await renderHistory({agentInstanceStatus: 'IDLE'});
		await expect.element(screen.getByText('Working on it')).toBeVisible();

		await screen.rerender(historyAt('IDLE', false));
		worker.use(mockHistory([textItem('2', 'ASSISTANT', 'All done'), textItem('1', 'ASSISTANT', 'Working on it')]));
		await screen.rerender(historyAt('COMPLETED', false));
		await screen.rerender(historyAt('COMPLETED', true));

		await expect.element(screen.getByText('All done')).toBeVisible();
	});

	it('should refetch the history when the agent status changes', async ({worker}) => {
		worker.use(mockHistory([textItem('1', 'ASSISTANT', 'Working on it')]));
		const screen = await renderHistory({agentInstanceStatus: 'IDLE'});
		await expect.element(screen.getByText('Working on it')).toBeVisible();

		worker.use(mockHistory([textItem('2', 'ASSISTANT', 'All done'), textItem('1', 'ASSISTANT', 'Working on it')]));
		await screen.rerender(
			<ConversationHistory
				agentInstanceKey="agent-1"
				agentInstanceStatus="COMPLETED"
				availableTools={[]}
				isVisible
				selectedElementInstanceKey={null}
				agentsElementInstanceKeys={[]}
			/>,
		);

		await expect.element(screen.getByText('All done')).toBeVisible();
	});
});
