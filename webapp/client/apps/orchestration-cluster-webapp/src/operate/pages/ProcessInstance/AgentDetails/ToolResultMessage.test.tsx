/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {render} from 'vitest-browser-react';
import type {AgentInstanceMessageContent, AgentInstanceToolCall} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {createDocumentReference} from '#/shared-test-modules/api-mocks/documents';
import {ToolResultMessage} from './ToolResultMessage';

vi.mock('#/operate/shared/Editors/LazyMonacoEditor', () => import('#/shared-test-modules/lazy-monaco-editor'));

const TOOL_CALL: AgentInstanceToolCall = {
	toolCallId: 'call-1',
	toolName: 'getWeather',
	elementId: null,
	arguments: {city: 'Berlin'},
};

function renderToolResult({
	content,
	toolCalls = [TOOL_CALL],
}: {
	content: AgentInstanceMessageContent[];
	toolCalls?: AgentInstanceToolCall[];
}) {
	return render(
		<div data-testid="container">
			<ToolResultMessage
				availableTools={[{name: 'getWeather', description: 'Returns the current weather', elementId: null}]}
				toolCalls={toolCalls}
				content={content}
			/>
		</div>,
	);
}

describe('<ToolResultMessage />', () => {
	it('should render nothing without tool call information', async () => {
		const screen = await renderToolResult({content: [{contentType: 'TEXT', text: 'Sunny'}], toolCalls: []});

		await expect.element(screen.getByTestId('container')).toBeEmptyDOMElement();
	});

	it.for([
		['text', [{contentType: 'TEXT', text: 'Sunny, 24°C'}], 'Sunny, 24°C'],
		['object', [{contentType: 'OBJECT', object: {temperature: 24, unit: 'C'}}], '{"temperature":24,"unit":"C"}'],
		['empty', [], 'The tool call did not return content.'],
	] as const)('should render a single-line preview for %s results', async ([, content, preview]) => {
		const screen = await renderToolResult({content: [...content]});

		const result = screen.getByRole('article', {name: 'Result for "getWeather" tool call'});
		await expect.element(result).toHaveTextContent(`getWeather${preview}Expand`);
	});

	it('should render document chips without an empty-result fallback', async () => {
		const screen = await renderToolResult({
			content: [
				{contentType: 'DOCUMENT', documentReference: createDocumentReference({metadata: {fileName: 'forecast.pdf'}})},
			],
		});

		await expect.element(screen.getByRole('list', {name: 'Documents'})).toHaveTextContent('forecast.pdf');
		await expect.element(screen.getByText('The tool call did not return content.')).not.toBeInTheDocument();

		await userEvent.click(screen.getByRole('button', {name: 'View documents'}));

		await expect.element(screen.getByRole('dialog', {name: '1 document in tool result'})).toBeVisible();
	});

	it('should open the tool call details with input and output', async () => {
		const screen = await renderToolResult({content: [{contentType: 'TEXT', text: 'Sunny'}]});

		await userEvent.click(screen.getByRole('button', {name: 'Expand'}));

		const dialog = screen.getByRole('dialog', {name: 'Tool call: getWeather'});
		await expect.element(dialog.getByText('Returns the current weather')).toBeVisible();
		await expect
			.element(dialog.getByRole('region', {name: 'Input'}).getByRole('textbox', {name: 'Value'}))
			.toBeInTheDocument();
		await expect
			.element(dialog.getByRole('region', {name: 'Output'}).getByRole('textbox', {name: 'Value'}))
			.toBeInTheDocument();

		await userEvent.click(dialog.getByRole('button', {name: 'Close'}));
		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Expand'})).toHaveFocus();
	});

	it('should show hints for missing input and output in the tool call details', async () => {
		const screen = await renderToolResult({content: [], toolCalls: [{...TOOL_CALL, arguments: null}]});

		await userEvent.click(screen.getByRole('button', {name: 'Expand'}));

		const dialog = screen.getByRole('dialog', {name: 'Tool call: getWeather'});
		await expect
			.element(dialog.getByRole('region', {name: 'Input'}))
			.toHaveTextContent('InputTool called without input arguments.');
		await expect
			.element(dialog.getByRole('region', {name: 'Output'}))
			.toHaveTextContent('OutputThe tool call did not return content.');
	});
});
