/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect, vi, beforeAll} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup, render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {RichTextEditor} from './RichTextEditor';
import {preloadMonaco} from '#/vitest-modules/preload-monaco';
import {AccountMenu} from '#/shared/header/shadcn.components/AccountMenu';
import {ThemeProvider} from '#/shared/theme/shadcn.components/ThemeProvider';
import {themeStore} from '#/shared/theme/theme';

describe('RichTextEditor', () => {
	beforeAll(preloadMonaco, 120_000);

	afterEach(async () => {
		await cleanup();
		themeStore.reset();
		localStorage.clear();
	});

	it('should never show the light editor background while mounting in dark mode', async () => {
		themeStore.seedTheme('dark');
		const backgrounds = new Set<string>();
		const observer = new MutationObserver(() => {
			const element = document.querySelector('[role="code"]');
			if (element !== null) {
				backgrounds.add(getComputedStyle(element).backgroundColor);
			}
		});
		observer.observe(document.body, {childList: true, subtree: true, attributes: true});
		const screen = await render(
			<ThemeProvider>
				<style>{'body .c4-ui.dark{--input-background:#0a0b0c}'}</style>
				<RichTextEditor theme="ds" value="true" autoFocus={false} />
			</ThemeProvider>,
		);

		await expect.element(screen.getByRole('textbox', {name: 'Value', exact: true})).toBeVisible();
		await expect
			.poll(() => getComputedStyle(screen.getByRole('code').element()).backgroundColor)
			.toBe('rgb(10, 11, 12)');
		observer.disconnect();

		expect([...backgrounds]).toEqual(['rgb(10, 11, 12)']);
	});

	it('should expose the editor id and invalid state on the Monaco input', async () => {
		const screen = await render(
			<RichTextEditor id="variable-value" value="true" isInvalid onChange={vi.fn()} autoFocus={false} />,
		);
		const editor = screen.getByRole('textbox', {name: 'Value', exact: true});

		await expect.element(editor).toHaveAttribute('id', 'variable-value-editor');
		await expect.element(editor).toHaveAttribute('aria-invalid', 'true');
	});

	it('should report edits through onChange', async () => {
		const onChange = vi.fn();
		const screen = await render(
			<RichTextEditor id="variable-value" value="true" onChange={onChange} autoFocus={false} />,
		);

		await userEvent.click(screen.getByText('true', {exact: true}));
		await userEvent.keyboard('{End}x');

		await expect.poll(() => onChange).toHaveBeenCalledWith('truex');
	});

	it('should paint the editor with the design system input background when the design system theme is requested', async () => {
		const screen = await render(
			<div className="c4-ui" style={{['--input-background' as string]: '#123456'}}>
				<RichTextEditor theme="ds" value="true" autoFocus={false} />
			</div>,
		);

		await expect
			.poll(() => getComputedStyle(screen.getByRole('code').element()).backgroundColor)
			.toBe('rgb(18, 52, 86)');
	});

	it('should paint the design system theme by default', async () => {
		const screen = await render(
			<div className="c4-ui" style={{['--input-background' as string]: '#123456'}}>
				<RichTextEditor value="true" autoFocus={false} />
			</div>,
		);

		await expect
			.poll(() => getComputedStyle(screen.getByRole('code').element()).backgroundColor)
			.toBe('rgb(18, 52, 86)');
	});

	it('should paint the design system colors when the theme switches from stock to design system after mount', async () => {
		const style = <style>{'body .c4-ui{--input-background:#fedcba}'}</style>;
		const screen = await render(
			<div className="c4-ui">
				{style}
				<RichTextEditor theme="stock" value="true" autoFocus={false} />
			</div>,
		);
		const editorBackground = () => getComputedStyle(screen.getByRole('code').element()).backgroundColor;

		await expect.poll(editorBackground).toBe('rgb(255, 255, 254)');

		await screen.rerender(
			<div className="c4-ui">
				{style}
				<RichTextEditor theme="ds" value="true" autoFocus={false} />
			</div>,
		);

		await expect.poll(editorBackground).toBe('rgb(254, 220, 186)');
	});

	it('should repaint the editor with the design system colors of the color mode the user switches to', async () => {
		const screen = await render(
			<ThemeProvider>
				<style>{'body .c4-ui{--input-background:#abcdef}body .c4-ui.dark{--input-background:#0a0b0c}'}</style>
				<AccountMenu displayName="Demo User" canLogout onLogout={() => {}} />
				<RichTextEditor theme="ds" value="true" autoFocus={false} />
			</ThemeProvider>,
		);
		const editorBackground = () =>
			getComputedStyle(screen.getByRole('code', {includeHidden: true}).element()).backgroundColor;

		await userEvent.click(screen.getByRole('button', {name: 'Settings'}));
		await userEvent.click(screen.getByRole('radio', {name: 'Light'}));
		await expect.poll(editorBackground).toBe('rgb(171, 205, 239)');

		await userEvent.click(screen.getByRole('radio', {name: 'Dark'}));
		await expect.poll(editorBackground).toBe('rgb(10, 11, 12)');
	});

	it('should keep a readable editor background when the design system token cannot be resolved', async () => {
		const screen = await render(
			<div className="c4-ui" style={{['--input-background' as string]: 'not-a-color'}}>
				<RichTextEditor theme="ds" value="true" autoFocus={false} />
			</div>,
		);

		await expect.element(screen.getByRole('textbox', {name: 'Value', exact: true})).toBeVisible();
		expect(getComputedStyle(screen.getByRole('code').element()).backgroundColor).toBe('rgb(255, 255, 254)');
	});

	it('should keep the stock editor background when the stock theme is requested', async () => {
		const screen = await render(
			<div className="c4-ui" style={{['--input-background' as string]: '#123456'}}>
				<RichTextEditor theme="stock" value="true" autoFocus={false} />
			</div>,
		);

		await expect.element(screen.getByRole('textbox', {name: 'Value', exact: true})).toBeVisible();
		expect(getComputedStyle(screen.getByRole('code').element()).backgroundColor).toBe('rgb(255, 255, 254)');
	});
});
