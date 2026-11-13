/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {afterEach, beforeEach, describe, expect, onTestFinished, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup, render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {themeStore} from '#/shared/theme/theme';
import {RichTextEditorModal} from './RichTextEditorModal';

describe('Editors (design system)', () => {
	beforeEach(() => {
		notificationsStore.reset();
	});
	afterEach(async () => {
		const monaco = await import('monaco-editor');
		const models = monaco.editor.getModels().filter((model) => model.getLanguageId() === 'json');
		if (models.length > 0) {
			const getWorker = await monaco.json.getWorker();
			await Promise.all(
				models.map(async (model) => {
					const worker = await getWorker(model.uri);
					await worker.parseJSONDocument(model.uri.toString());
				}),
			);
		}
		await cleanup();
		notificationsStore.reset();
		themeStore.reset();
	});

	describe('RichTextEditorModal', () => {
		function Host({value, ...props}: Partial<React.ComponentProps<typeof RichTextEditorModal>> & {value: string}) {
			const [applied, setApplied] = useState<string | null>(null);
			const [isVisible, setIsVisible] = useState(true);

			return (
				<>
					<output aria-label="Applied value">{applied}</output>
					<button type="button" onClick={() => setIsVisible(true)}>
						Reopen
					</button>
					<RichTextEditorModal
						title="Edit payload"
						{...props}
						value={value}
						isVisible={isVisible}
						onApply={setApplied}
						onClose={() => setIsVisible(false)}
					/>
				</>
			);
		}

		it('should reject invalid JSON when applying', async () => {
			const screen = await render(<Host value="invalid" />);

			await userEvent.click(screen.getByRole('button', {name: 'Apply'}));

			await expect.element(screen.getByRole('alert').filter({hasText: 'Enter a valid JSON value.'})).toBeVisible();
			await expect
				.element(screen.getByRole('textbox', {name: 'Value', exact: true}))
				.toHaveAttribute('aria-invalid', 'true');
			await expect.element(screen.getByLabelText('Applied value')).toBeEmptyDOMElement();
		});

		it('should apply valid JSON in compact form', async () => {
			const screen = await render(<Host value={'{\n "a": 1\n}'} />);

			await userEvent.click(screen.getByRole('button', {name: 'Apply'}));

			await expect.element(screen.getByLabelText('Applied value')).toHaveTextContent('{"a":1}');
		});

		it('should discard edits when switching back to view mode', async () => {
			const screen = await render(
				<Host
					value="true"
					readOnly
					allowModeToggle
					variableName="enabled"
					title="View value"
					editModeTitle="Edit value"
				/>,
			);
			await userEvent.click(screen.getByRole('button', {name: 'Edit', exact: true}));
			await expect.element(screen.getByRole('heading', {name: 'Edit value'})).toBeVisible();
			await userEvent.click(screen.getByText('true', {exact: true}));
			await userEvent.keyboard('{End}x');

			await userEvent.click(screen.getByRole('button', {name: 'View', exact: true}));

			await expect.element(screen.getByText('true', {exact: true})).toBeVisible();
			await expect.element(screen.getByText('truex')).not.toBeInTheDocument();
		});

		it('should copy the variable key with its value', async () => {
			const writeText = vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue();
			onTestFinished(() => writeText.mockRestore());
			const screen = await render(<Host value="true" readOnly variableName="enabled" title="View value" />);

			await userEvent.click(screen.getByRole('button', {name: 'Copy', exact: true}));

			await expect.poll(() => writeText.mock.calls).toEqual([['{"enabled":true}']]);
		});

		it('should preserve markdown without JSON parsing', async () => {
			const screen = await render(<Host value="# Notes" language="markdown" variableName="message" />);
			await expect.element(screen.getByRole('textbox', {name: 'Value', exact: true})).toHaveFocus();
			await userEvent.keyboard('{ArrowRight>7/}!');

			await userEvent.click(screen.getByRole('button', {name: 'Apply'}));

			await expect.element(screen.getByLabelText('Applied value')).toHaveTextContent('# Notes!');
		});

		it('should reset edits when reopened', async () => {
			const screen = await render(<Host value="# Notes" language="markdown" variableName="message" />);
			await expect.element(screen.getByRole('textbox', {name: 'Value', exact: true})).toHaveFocus();
			await userEvent.keyboard('{ArrowRight>7/}!');
			await userEvent.click(screen.getByRole('button', {name: 'Cancel'}));
			await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();

			await userEvent.click(screen.getByRole('button', {name: 'Reopen'}));
			await userEvent.click(screen.getByRole('button', {name: 'Apply'}));

			await expect.element(screen.getByLabelText('Applied value')).toHaveTextContent('# Notes');
		});
	});
});
