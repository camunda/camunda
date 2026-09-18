/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {afterEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import i18n from 'i18next';
import {it} from '#/vitest-modules/test-extend';
import {themeStore} from '#/shared/theme/theme';
import {AccountMenu} from './AccountMenu';

describe('User info', () => {
	afterEach(async () => {
		themeStore.reset();
		await i18n.changeLanguage('en');
		localStorage.clear();
	});

	it('should render user display name', async () => {
		const screen = await render(<AccountMenu displayName="Demo User" canLogout onLogout={() => {}} />);

		await userEvent.click(screen.getByRole('button', {name: 'Settings'}));

		await expect.element(screen.getByText('Demo User')).toBeVisible();
	});

	it('should render language selection dropdown', async () => {
		const screen = await render(<AccountMenu displayName="Demo User" canLogout onLogout={() => {}} />);

		await userEvent.click(screen.getByRole('button', {name: 'Settings'}));

		await expect.element(screen.getByRole('radiogroup', {name: 'Language'})).toBeVisible();
		await expect.element(screen.getByRole('radio', {name: 'English'})).toBeChecked();
	});

	it('should handle a SSO user', async () => {
		const screen = await render(<AccountMenu displayName="Demo User" canLogout={false} onLogout={() => {}} />);

		await userEvent.click(screen.getByRole('button', {name: 'Settings'}));

		await expect.element(screen.getByText('Demo User')).toBeVisible();
		expect(screen.getByRole('menuitem', {name: 'Log out'}).elements()).toHaveLength(0);
	});

	it('should handle logout', async () => {
		const handleLogout = vi.fn();
		const screen = await render(<AccountMenu displayName="Demo User" canLogout onLogout={handleLogout} />);

		await userEvent.click(screen.getByRole('button', {name: 'Settings'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Log out'}));

		expect(handleLogout).toHaveBeenCalledOnce();
	});

	it('should render links', async () => {
		const mockOpenFn = vi.fn();
		vi.stubGlobal('open', mockOpenFn);
		const screen = await render(<AccountMenu displayName="Demo User" canLogout onLogout={() => {}} />);

		await userEvent.click(screen.getByRole('button', {name: 'Settings'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Terms of use'}));
		expect(mockOpenFn).toHaveBeenLastCalledWith(
			'https://camunda.com/legal/terms/camunda-platform/camunda-platform-8-saas-trial/',
			'_blank',
			'noopener,noreferrer',
		);

		await userEvent.click(screen.getByRole('button', {name: 'Settings'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Privacy policy'}));
		expect(mockOpenFn).toHaveBeenLastCalledWith('https://camunda.com/legal/privacy/', '_blank', 'noopener,noreferrer');

		await userEvent.click(screen.getByRole('button', {name: 'Settings'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Imprint'}));
		expect(mockOpenFn).toHaveBeenLastCalledWith('https://camunda.com/legal/imprint/', '_blank', 'noopener,noreferrer');

		expect(screen.getByRole('menuitem', {name: 'Cookie preferences'}).elements()).toHaveLength(0);
	});

	it('should cookie preferences with correct link', async () => {
		const handleCookiePreferences = vi.fn();
		const screen = await render(
			<AccountMenu
				displayName="Demo User"
				canLogout
				onLogout={() => {}}
				onOpenCookiePreferences={handleCookiePreferences}
			/>,
		);

		await userEvent.click(screen.getByRole('button', {name: 'Settings'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'Cookie preferences'}));

		expect(handleCookiePreferences).toHaveBeenCalledOnce();
	});

	it('should update the selected theme', async () => {
		const screen = await render(<AccountMenu displayName="Demo User" canLogout onLogout={() => {}} />);

		expect(themeStore.selectedTheme).toBe('system');
		expect(localStorage.getItem('theme')).toBeNull();

		await userEvent.click(screen.getByRole('button', {name: 'Settings'}));

		await expect.element(screen.getByRole('radiogroup', {name: 'Theme'})).toBeVisible();
		await expect.element(screen.getByRole('radio', {name: 'System'})).toBeChecked();
		await userEvent.click(screen.getByRole('radio', {name: 'Light'}));

		expect(themeStore.selectedTheme).toBe('light');
		expect(localStorage.getItem('theme')).toBe('"light"');
		await expect.element(screen.getByRole('radio', {name: 'Light'})).toBeChecked();
		await expect.element(screen.getByRole('radio', {name: 'System'})).not.toBeChecked();
	});

	it('should update the selected language', async () => {
		const changeLanguageSpy = vi.spyOn(i18n, 'changeLanguage');
		const screen = await render(<AccountMenu displayName="Demo User" canLogout onLogout={() => {}} />);

		expect(i18n.resolvedLanguage).toBe('en');
		expect(localStorage.getItem('language')).toBeNull();

		await userEvent.click(screen.getByRole('button', {name: 'Settings'}));
		await userEvent.click(screen.getByRole('radio', {name: 'Deutsch'}));

		expect(changeLanguageSpy).toHaveBeenCalledWith('de');
		expect(localStorage.getItem('language')).toBe('de');
		await expect.element(screen.getByRole('radio', {name: 'Deutsch'})).toBeChecked();
		await expect.element(screen.getByRole('radio', {name: 'English'})).not.toBeChecked();
	});
});
