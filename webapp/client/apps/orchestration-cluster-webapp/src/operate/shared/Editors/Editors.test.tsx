/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterAll, afterEach, beforeAll, beforeEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup, render} from 'vitest-browser-react';
import {useState} from 'react';
import {it} from '#/vitest-modules/test-extend';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {themeStore} from '#/shared/theme/theme';
import {InlineJsonEditor} from './InlineJsonEditor';
import {RichTextEditor, type EditorHandle} from './RichTextEditor';
import {RichTextEditorModal} from './RichTextEditorModal';

describe('Editors', () => {
	beforeAll(() => {
		vi.useFakeTimers({toFake: ['setTimeout', 'clearTimeout', 'Date'], shouldAdvanceTime: true});
	});
	afterAll(() => vi.useRealTimers());
	beforeEach(() => {
		vi.spyOn(navigator.clipboard, 'writeText').mockResolvedValue();
		notificationsStore.reset();
	});
	afterEach(async () => {
		await vi.advanceTimersByTimeAsync(100);
		const monaco = await import('monaco-editor');
		const models = monaco.editor.getModels().filter((model) => model.getLanguageId() === 'json');
		if (models.length > 0) {
			const getWorker = await monaco.languages.json.getWorker();
			await Promise.all(
				models.map(async (model) => {
					const worker = await getWorker(model.uri);
					await worker.parseJSONDocument(model.uri.toString());
				}),
			);
		}
		await cleanup();
		vi.restoreAllMocks();
		notificationsStore.reset();
		themeStore.reset();
	});

	describe('InlineJsonEditor', () => {
		it.for([
			['{"a":1}', '{\n\t"a": 1\n}'],
			['[1,2]', '[\n\t1,\n\t2\n]'],
			['true', 'true'],
			['null', 'null'],
			['invalid JSON', 'invalid JSON'],
		])('should display and copy formatted JSON: %s', async ([value = '', expected = '']) => {
			const screen = await render(<InlineJsonEditor value={value} label="payload" />);
			const copy = screen.getByRole('button', {name: 'Copy payload'});
			expect(copy.element().textContent).toBe(expected);
			await userEvent.click(copy);
			expect(navigator.clipboard.writeText).toHaveBeenCalledWith(expected);
			expect(notificationsStore.notifications[0]?.title).toBe('Copied payload to clipboard');
		});

		it.for([
			['{"a":[1,2', '{\n\t"a": [\n\t\t1,\n\t\t2\n\t'],
			['{"a":[],"b":', '{\n\t"a": []\n'],
			['{"a":tru', '{\n\t"a": true\n'],
			['"hello', '"hello"'],
			['{"a":1,', '{\n\t"a": 1\n'],
		])('should preserve truncated formatting: %s', async ([value = '', expected = '']) => {
			const screen = await render(<InlineJsonEditor value={value} isTruncatedValue />);
			expect(screen.getByRole('button', {name: 'Copy value'}).element().textContent).toBe(expected);
		});

		it('should fetch the full value once and support keyboard copying', async () => {
			let resolveCopy!: (value: string) => void;
			const pending = new Promise<string>((resolve) => {
				resolveCopy = resolve;
			});
			const onCopy = vi.fn(() => pending);
			const screen = await render(<InlineJsonEditor value='"truncated' isTruncatedValue onCopy={onCopy} />);
			const copy = screen.getByRole('button', {name: 'Copy value'});
			await userEvent.tab();
			await userEvent.keyboard('{Enter}');
			await expect.element(copy).toHaveAttribute('aria-disabled', 'true');
			await expect.element(screen.getByTestId('copy-loading-indicator')).toHaveStyle({position: 'absolute'});
			await userEvent.keyboard(' ');
			expect(onCopy).toHaveBeenCalledOnce();
			expect(navigator.clipboard.writeText).not.toHaveBeenCalled();
			resolveCopy('"full value"');
			await expect.element(copy).toHaveAttribute('aria-disabled', 'false');
			expect(navigator.clipboard.writeText).toHaveBeenCalledWith('"full value"');
		});

		it('should report a full-value fetch failure without copying a truncated value', async () => {
			const screen = await render(
				<InlineJsonEditor
					value='"partial'
					onCopy={async () => {
						throw new Error('Unavailable');
					}}
				/>,
			);
			await userEvent.click(screen.getByRole('button', {name: 'Copy value'}));
			expect(navigator.clipboard.writeText).not.toHaveBeenCalled();
			expect(notificationsStore.notifications[0]?.title).toBe('Failed to fetch full value');
		});

		it('should report clipboard failure and remain usable', async () => {
			vi.mocked(navigator.clipboard.writeText).mockRejectedValueOnce(new Error('Denied'));
			const screen = await render(<InlineJsonEditor value="42" />);
			await userEvent.click(screen.getByRole('button', {name: 'Copy value'}));
			expect(notificationsStore.notifications[0]?.title).toBe('Failed to copy value to clipboard');
			await userEvent.click(screen.getByRole('button', {name: 'Copy value'}));
			expect(notificationsStore.notifications[0]?.kind).toBe('success');
		});

		it.for(['short', 'large'])('should keep read-only content bounded without clipping controls: %s', async (size) => {
			const value = JSON.stringify(size === 'short' ? {a: 1} : Array.from({length: 1000}, () => 'a'.repeat(200)));
			const showAll = vi.fn();
			const screen = await render(
				<InlineJsonEditor
					value={value}
					label="payload"
					maxLines={3}
					fieldError="Invalid value"
					renderButton={() => <button onClick={showAll}>Show all</button>}
				/>,
			);
			const content = screen.getByRole('button', {name: 'Copy payload'}).element();
			const button = screen.getByRole('button', {name: 'Show all'});
			expect(content.getBoundingClientRect().height).toBeLessThanOrEqual(60);
			expect(button.element().getBoundingClientRect().bottom).toBeLessThanOrEqual(
				content.parentElement!.getBoundingClientRect().bottom,
			);
			expect(getComputedStyle(content).overflowWrap).toBe('anywhere');
			await userEvent.click(button);
			expect(showAll).toHaveBeenCalledOnce();
			await expect
				.element(screen.getByRole('alert').filter({hasText: 'Invalid value'}))
				.toHaveTextContent('Invalid value');
		});

		it('should edit, validate and blur on Escape without losing changes', async () => {
			const onValidate = vi.fn();
			const onBlur = vi.fn();
			const onFocus = vi.fn();
			const Form = () => {
				const [value, setValue] = useState('');
				return (
					<InlineJsonEditor
						value={value}
						onChange={setValue}
						onValidate={onValidate}
						onBlur={onBlur}
						onFocus={onFocus}
						isModified
						autoFocus
					/>
				);
			};
			const screen = await render(<Form />);
			const editor = screen.getByRole('textbox', {name: 'Value', exact: true});
			await expect.element(editor).toHaveFocus();
			await userEvent.keyboard('true');
			expect(onValidate).toHaveBeenLastCalledWith(true);
			await userEvent.keyboard('x');
			expect(onValidate).toHaveBeenLastCalledWith(false);
			await userEvent.keyboard('{Escape}');
			await expect.element(editor).not.toHaveFocus();
			expect(onBlur).toHaveBeenCalledOnce();
			expect(onFocus).toHaveBeenCalledOnce();
			await expect.element(screen.getByText('truex', {exact: true})).toBeVisible();
		});

		it('should expose and clear field errors on the editable input', async () => {
			const onChange = vi.fn();
			const screen = await render(
				<InlineJsonEditor id="variable-value" value="false" onChange={onChange} fieldError="Invalid value" />,
			);
			const editor = screen.getByRole('textbox', {name: 'Value', exact: true});
			await expect.element(editor).toHaveAttribute('id', 'variable-value-editor');
			await expect.element(editor).toHaveAttribute('aria-invalid', 'true');
			await screen.rerender(<InlineJsonEditor id="variable-value" value="false" onChange={onChange} />);
			await expect.element(editor).toHaveAttribute('aria-invalid', 'false');
		});

		it('should preserve the editor during external updates and allow Tab to leave it', async () => {
			const onChange = vi.fn();
			const screen = await render(
				<>
					<InlineJsonEditor value="true" onChange={onChange} />
					<button>Next</button>
				</>,
			);
			const editor = screen.getByRole('textbox', {name: 'Value', exact: true});
			await expect.element(editor).toBeVisible();
			const original = editor.element();
			await screen.rerender(
				<>
					<InlineJsonEditor value="false" onChange={onChange} />
					<button>Next</button>
				</>,
			);
			expect(editor.element()).toBe(original);
			expect(onChange).not.toHaveBeenCalled();
			await userEvent.click(screen.getByText('false', {exact: true}));
			await userEvent.tab();
			await expect.element(screen.getByRole('button', {name: 'Next'})).toHaveFocus();
		});
	});

	describe('RichTextEditorModal', () => {
		it('should apply compact JSON and reject invalid initial input', async () => {
			const onApply = vi.fn();
			const screen = await render(
				<RichTextEditorModal isVisible value="invalid" onApply={onApply} title="Edit payload" />,
			);
			await userEvent.click(screen.getByRole('button', {name: 'Apply'}));
			await expect.element(screen.getByRole('alert').filter({hasText: 'Enter a valid JSON value.'})).toBeVisible();
			await expect
				.element(screen.getByRole('textbox', {name: 'Value', exact: true}))
				.toHaveAttribute('aria-invalid', 'true');
			expect(onApply).not.toHaveBeenCalled();
			await vi.advanceTimersByTimeAsync(100);
			await screen.rerender(
				<RichTextEditorModal isVisible value={'{\n "a": 1\n}'} onApply={onApply} title="Edit payload" />,
			);
			await expect
				.element(screen.getByRole('textbox', {name: 'Value', exact: true}))
				.toHaveAttribute('aria-invalid', 'false');
			await userEvent.click(screen.getByRole('button', {name: 'Apply'}));
			expect(onApply).toHaveBeenCalledWith('{"a":1}');
		});

		it('should switch modes, discard edits on View and copy the variable key with its value', async () => {
			const screen = await render(
				<RichTextEditorModal
					isVisible
					value="true"
					readOnly
					allowModeToggle
					variableName="enabled"
					title="View value"
					editModeTitle="Edit value"
				/>,
			);
			await expect.element(screen.getByRole('button', {name: 'Apply'})).not.toBeInTheDocument();
			await userEvent.click(screen.getByRole('button', {name: 'Edit', exact: true}));
			await expect.element(screen.getByRole('heading', {name: 'Edit value'})).toBeVisible();
			await userEvent.click(screen.getByText('true', {exact: true}));
			await userEvent.keyboard('{End}x');
			await userEvent.click(screen.getByRole('button', {name: 'View', exact: true}));
			await userEvent.click(screen.getByRole('button', {name: 'Copy', exact: true}));
			expect(navigator.clipboard.writeText).toHaveBeenCalledWith('{"enabled":true}');
		});

		it('should reset when reopened and preserve markdown without JSON parsing', async () => {
			const onApply = vi.fn();
			const onClose = vi.fn();
			const props = {value: '# Notes', language: 'markdown', variableName: 'message', onApply, onClose} as const;
			const screen = await render(<RichTextEditorModal {...props} isVisible />);
			const editor = screen.getByRole('textbox', {name: 'Value', exact: true});
			await expect.element(editor).toHaveFocus();
			await userEvent.keyboard('{ArrowRight>7/}!');
			await userEvent.click(screen.getByRole('button', {name: 'Copy', exact: true}));
			expect(navigator.clipboard.writeText).toHaveBeenCalledWith('# Notes!');
			await userEvent.click(screen.getByRole('button', {name: 'Apply'}));
			expect(onApply).toHaveBeenCalledWith('# Notes!');
			await userEvent.click(screen.getByRole('button', {name: 'Cancel'}));
			expect(onClose).toHaveBeenCalledOnce();
			await vi.advanceTimersByTimeAsync(100);
			await screen.rerender(<RichTextEditorModal {...props} isVisible={false} />);
			await expect.element(screen.getByRole('dialog')).not.toBeInTheDocument();
			await screen.rerender(<RichTextEditorModal {...props} isVisible />);
			await userEvent.click(screen.getByRole('button', {name: 'Apply'}));
			expect(onApply).toHaveBeenLastCalledWith('# Notes');
		});
	});

	describe('RichTextEditor', () => {
		it('should show and hide validation markers through its public handle', async () => {
			let onValidate!: (isValid: boolean) => void;
			let handle!: EditorHandle;
			const invalid = new Promise<boolean>((resolve) => {
				onValidate = resolve;
			});
			const screen = await render(
				<RichTextEditor
					value="!"
					onValidate={onValidate}
					onMount={(editor) => {
						handle = editor;
					}}
				/>,
			);
			expect(await invalid).toBe(false);
			handle.showMarkers();
			const close = screen.getByRole('button', {name: /Close/});
			await expect.element(close).toBeVisible();
			handle.hideMarkers();
			await expect.element(close).not.toBeInTheDocument();
		});

		it('should update theme without replacing content', async () => {
			themeStore.changeTheme('light');
			const screen = await render(<RichTextEditor value='"hello"' readOnly />);
			const editor = screen.getByRole('textbox', {name: 'Value', exact: true});
			await expect.element(editor).toBeVisible();
			const original = editor.element();
			const lightColor = getComputedStyle(original).color;
			themeStore.changeTheme('dark');
			await expect.poll(() => getComputedStyle(original).color).not.toBe(lightColor);
			expect(editor.element()).toBe(original);
		});

		it('should validate against its schema and clear diagnostics when the schema is removed', async () => {
			let onValidate!: (isValid: boolean) => void;
			const invalid = new Promise<boolean>((resolve) => {
				onValidate = resolve;
			});
			const screen = await render(
				<RichTextEditor
					value='{"count":"invalid"}'
					jsonSchema={{type: 'object', properties: {count: {type: 'number'}}}}
					onValidate={onValidate}
				/>,
			);
			expect(await invalid).toBe(false);
			const valid = new Promise<boolean>((resolve) => {
				onValidate = resolve;
			});
			await screen.rerender(<RichTextEditor value='{"count":"invalid"}' onValidate={onValidate} />);
			expect(await valid).toBe(true);
		});

		it('should keep schemas isolated between editor models', async () => {
			let validateNumber!: (isValid: boolean) => void;
			let validateString!: (isValid: boolean) => void;
			const invalidNumber = new Promise<boolean>((resolve) => {
				validateNumber = resolve;
			});
			const invalidString = new Promise<boolean>((resolve) => {
				validateString = resolve;
			});
			const numberSchema = {type: 'number'};
			const stringSchema = {type: 'string'};
			const onValidateNumber = vi.fn(validateNumber);
			const screen = await render(
				<>
					<RichTextEditor value='"text"' jsonSchema={numberSchema} onValidate={onValidateNumber} />
					<RichTextEditor value="42" jsonSchema={stringSchema} onValidate={validateString} />
				</>,
			);
			expect(await Promise.all([invalidNumber, invalidString])).toEqual([false, false]);
			const validString = new Promise<boolean>((resolve) => {
				validateString = resolve;
			});
			await screen.rerender(
				<>
					<RichTextEditor value='"text"' jsonSchema={numberSchema} onValidate={onValidateNumber} />
					<RichTextEditor value='"text"' jsonSchema={stringSchema} onValidate={validateString} />
				</>,
			);
			expect(await validString).toBe(true);
			expect(onValidateNumber).toHaveBeenLastCalledWith(false);
		});
	});
});
