/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Dialog, DialogContent, DialogTitle, useC4Dictionary} from '@camunda/design-system';
import i18n from 'i18next';
import {render} from 'vitest-browser-react';
import {afterEach, describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {ThemeProvider} from './ThemeProvider';

const DictionaryProbe: React.FC = () => {
	const {t} = useC4Dictionary();

	return (
		<>
			<span>{t('appSidebar.expand')}</span>
			<span>{t('notifications.totalCount', {count: 2})}</span>
		</>
	);
};

describe('<ThemeProvider />', () => {
	afterEach(async () => {
		await i18n.changeLanguage('en');
	});

	it('should update the design-system dictionary when the language changes', async () => {
		const screen = await render(
			<ThemeProvider>
				<DictionaryProbe />
			</ThemeProvider>,
		);

		await expect.element(screen.getByText('Expand sidebar')).toBeVisible();

		await i18n.changeLanguage('de');

		await expect.element(screen.getByText('Seitenleiste erweitern')).toBeVisible();
		await expect.element(screen.getByText('2 Benachrichtigungen')).toBeVisible();
	});

	it('should pass the dictionary through portaled design-system components', async () => {
		await i18n.changeLanguage('fr');

		const screen = await render(
			<ThemeProvider>
				<Dialog defaultOpen>
					<DialogContent aria-describedby={undefined}>
						<DialogTitle>Example</DialogTitle>
					</DialogContent>
				</Dialog>
			</ThemeProvider>,
		);

		await expect.element(screen.getByRole('button', {name: 'Fermer'})).toBeVisible();
	});
});
