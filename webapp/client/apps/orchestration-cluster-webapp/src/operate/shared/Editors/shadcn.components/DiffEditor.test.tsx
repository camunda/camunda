/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeAll, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup, render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {preloadMonaco} from '#/vitest-modules/preload-monaco';
import {AccountMenu} from '#/shared/header/shadcn.components/AccountMenu';
import {ThemeProvider} from '#/shared/theme/shadcn.components/ThemeProvider';
import {themeStore} from '#/shared/theme/theme';
import {DiffEditor} from './DiffEditor';

describe('DiffEditor', () => {
	beforeAll(preloadMonaco, 120_000);

	afterEach(async () => {
		await cleanup();
		themeStore.reset();
		localStorage.clear();
	});

	it('should never show the light diff background while mounting in dark mode', async () => {
		themeStore.seedTheme('dark');
		const backgrounds = new Set<string>();
		const observer = new MutationObserver(() => {
			document.querySelectorAll('.monaco-editor').forEach((element) => {
				backgrounds.add(getComputedStyle(element).backgroundColor);
			});
		});
		observer.observe(document.body, {childList: true, subtree: true, attributes: true});
		const screen = await render(
			<ThemeProvider>
				<style>{'body .c4-ui.dark{--input-background:#0a0b0c}'}</style>
				<DiffEditor originalValue='{"a":1}' modifiedValue='{"a":2}' height="100px" />
			</ThemeProvider>,
		);

		await expect
			.poll(() => getComputedStyle(screen.getByRole('code', {includeHidden: true}).first().element()).backgroundColor)
			.toBe('rgb(10, 11, 12)');
		observer.disconnect();

		expect([...backgrounds]).toEqual(['rgb(10, 11, 12)']);
	});

	it('should repaint the diff with the design system colors of the color mode the user switches to', async () => {
		const screen = await render(
			<ThemeProvider>
				<style>{'body .c4-ui{--input-background:#abcdef}body .c4-ui.dark{--input-background:#112233}'}</style>
				<AccountMenu displayName="Demo User" canLogout onLogout={() => {}} />
				<DiffEditor originalValue='{"a":1}' modifiedValue='{"a":2}' height="100px" />
			</ThemeProvider>,
		);
		const diffBackground = () =>
			getComputedStyle(screen.getByRole('code', {includeHidden: true}).first().element()).backgroundColor;

		await userEvent.click(screen.getByRole('button', {name: 'Settings'}));
		await userEvent.click(screen.getByRole('radio', {name: 'Light'}));
		await expect.poll(diffBackground).toBe('rgb(171, 205, 239)');

		await userEvent.click(screen.getByRole('radio', {name: 'Dark'}));
		await expect.poll(diffBackground).toBe('rgb(17, 34, 51)');
	});

	it('should paint the design system colors when the theme switches from stock to design system after mount', async () => {
		const style = <style>{'body .c4-ui{--input-background:#fedcba}'}</style>;
		const screen = await render(
			<div className="c4-ui">
				{style}
				<DiffEditor theme="stock" originalValue='{"a":1}' modifiedValue='{"a":2}' height="100px" />
			</div>,
		);
		const diffBackground = () =>
			getComputedStyle(screen.getByRole('code', {includeHidden: true}).first().element()).backgroundColor;

		await expect.poll(diffBackground).toBe('rgb(255, 255, 254)');

		await screen.rerender(
			<div className="c4-ui">
				{style}
				<DiffEditor theme="ds" originalValue='{"a":1}' modifiedValue='{"a":2}' height="100px" />
			</div>,
		);

		await expect.poll(diffBackground).toBe('rgb(254, 220, 186)');
	});
});
