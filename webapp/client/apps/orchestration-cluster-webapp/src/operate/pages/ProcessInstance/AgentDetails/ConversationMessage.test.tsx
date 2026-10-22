/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {createDocumentReference} from '#/shared-test-modules/api-mocks/documents';
import {ConversationMessage} from './ConversationMessage';

describe('<ConversationMessage />', () => {
	it.for([
		['SYSTEM', 'System', 'System prompt'],
		['USER', 'User', 'User message'],
		['ASSISTANT', 'Assistant', 'Assistant message'],
	] as const)('should label %s messages and open their markdown details', async ([actor, label, title]) => {
		const screen = await render(
			<ConversationMessage actor={actor} content={[{contentType: 'TEXT', text: 'Hello **world**'}]} />,
		);

		const message = screen.getByRole('article', {name: title});
		await expect.element(message.getByRole('heading', {name: label})).toBeVisible();

		await userEvent.click(message.getByRole('button', {name: 'Expand'}));

		const dialog = screen.getByRole('dialog', {name: title});
		await expect.element(dialog.getByRole('tab', {name: 'Preview'})).toHaveAttribute('aria-selected', 'true');
		await expect.element(dialog.getByRole('strong')).toHaveTextContent('world');
		await expect.element(dialog.getByRole('button', {name: 'Copy'})).toBeVisible();

		await userEvent.click(dialog.getByRole('tab', {name: 'Source'}));
		await expect.element(dialog.getByRole('textbox', {name: 'Value'})).toBeInTheDocument();

		await userEvent.click(dialog.getByRole('button', {name: 'Close'}));
		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
		await expect.element(message.getByRole('button', {name: 'Expand'})).toHaveFocus();
	});

	it('should reopen the details in preview mode', async () => {
		const screen = await render(
			<ConversationMessage actor="USER" content={[{contentType: 'TEXT', text: 'Plain text'}]} />,
		);

		await userEvent.click(screen.getByRole('button', {name: 'Expand'}));
		await userEvent.click(screen.getByRole('tab', {name: 'Source'}));
		await userEvent.keyboard('{Escape}');
		await userEvent.keyboard('{Escape}');
		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();

		await userEvent.click(screen.getByRole('button', {name: 'Expand'}));

		await expect.element(screen.getByRole('tab', {name: 'Preview'})).toHaveAttribute('aria-selected', 'true');
	});

	it('should show object content as formatted JSON with source-only details', async () => {
		const screen = await render(
			<ConversationMessage actor="ASSISTANT" content={[{contentType: 'OBJECT', object: {kind: 'metadata'}}]} />,
		);

		await expect.element(screen.getByText('"kind": "metadata"', {exact: false})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Expand'}));

		const dialog = screen.getByRole('dialog', {name: 'Assistant message'});
		await expect.element(dialog.getByRole('textbox', {name: 'Value'})).toBeInTheDocument();
		await expect.element(dialog.getByRole('tab', {name: 'Preview'})).not.toBeInTheDocument();
		await expect.element(dialog.getByRole('button', {name: 'Copy'})).toBeVisible();
	});

	it('should render assistant reasoning without its payload or actions', async () => {
		const reasoning = ['Review the complete evidence before deciding.', 'A'.repeat(500)].join('\n');
		const screen = await render(
			<ConversationMessage
				actor="ASSISTANT"
				content={[
					{
						contentType: 'OBJECT',
						object: {
							'camunda.agenticai.content.type': 'reasoning',
							payload: {signature: 'provider-secret'},
							text: reasoning,
						},
					},
					{
						contentType: 'OBJECT',
						object: {'camunda.agenticai.content.type': 'reasoning', text: ' \n\t '},
					},
					{contentType: 'TEXT', text: 'Final assistant answer'},
				]}
			/>,
		);

		const note = screen.getByRole('region', {name: 'Thinking'});
		await expect.element(note).toHaveTextContent(`Thinking${reasoning.replace('\n', ' ')}`);
		await expect.element(note.getByRole('button')).not.toBeInTheDocument();
		await expect.element(screen.getByText('provider-secret', {exact: false})).not.toBeInTheDocument();
		await expect.element(screen.getByText('Final assistant answer')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Expand'})).toBeInTheDocument();
	});

	it('should render reasoning objects of other actors as plain JSON', async () => {
		const screen = await render(
			<ConversationMessage
				actor="USER"
				content={[
					{
						contentType: 'OBJECT',
						object: {'camunda.agenticai.content.type': 'reasoning', text: 'Assistant-only reasoning'},
					},
				]}
			/>,
		);

		await expect.element(screen.getByText('"text": "Assistant-only reasoning"', {exact: false})).toBeVisible();
		await expect.element(screen.getByRole('region', {name: 'Thinking'})).not.toBeInTheDocument();
	});

	it('should render metrics, content, documents and tool calls in order', async () => {
		const screen = await render(
			<ConversationMessage
				actor="ASSISTANT"
				content={[
					{contentType: 'TEXT', text: 'Answer introduction'},
					{
						contentType: 'DOCUMENT',
						documentReference: createDocumentReference({metadata: {fileName: 'evidence.txt'}}),
					},
				]}
				metrics={{
					inputTokens: 2,
					outputTokens: 1,
					cacheCreationTokenCount: null,
					cacheReadTokenCount: null,
					reasoningTokenCount: 1,
					durationMs: 1500,
				}}
				toolCalls={[{toolCallId: 'tool-call', toolName: 'inspect_evidence', elementId: null, arguments: null}]}
			/>,
		);

		await expect
			.element(screen.getByRole('article', {name: 'Assistant message'}))
			.toMatchTextContent(
				/^Assistant3\stokensInput: 2Output: 1 \(1 reasoning\)1\.50sAnswer introduction.*Documentsevidence\.txt.*Tool callsinspect_evidence$/,
			);
	});

	it('should show at most three document chips and open the document list', async () => {
		const screen = await render(
			<ConversationMessage
				actor="USER"
				content={['a.pdf', 'b.pdf', 'very-long-document-file-name.pdf', 'd.pdf', 'e.pdf'].map((fileName) => ({
					contentType: 'DOCUMENT' as const,
					documentReference: createDocumentReference({documentId: fileName, metadata: {fileName}}),
				}))}
			/>,
		);

		const documents = screen.getByRole('list', {name: 'Documents'});
		await expect.element(documents).toHaveTextContent('a.pdfb.pdfvery-long…-name.pdf2 more');
		await expect.element(documents.getByTitle('very-long-document-file-name.pdf')).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'View documents'}));

		const dialog = screen.getByRole('dialog', {name: '5 documents in conversation message'});
		await expect.element(dialog.getByRole('listitem', {name: 'e.pdf'})).toBeVisible();

		await userEvent.click(dialog.getByRole('button', {name: 'Close'}));
		await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'View documents'})).toHaveFocus();
	});
});
