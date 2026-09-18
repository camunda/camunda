/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {getC4Dictionary, getC4Locale} from './c4-dictionaries';

const EXPECTED_KEYS = [
	'appHeader.skipToContent',
	'appSidebar.closeNavigation',
	'appSidebar.collapse',
	'appSidebar.collapseText',
	'appSidebar.expand',
	'dialog.close',
	'navBreadcrumb.actionsLabel',
	'navBreadcrumb.hiddenLevels',
	'navBreadcrumb.switchContext',
	'notifications.dismissAll',
	'notifications.dismissItem',
	'notifications.totalCount',
	'notifications.triggerLabel',
	'notifications.unreadCount',
	'notifications.unreadPrefix',
	'sheet.close',
	'sidebarProvider.closeNavigation',
	'sidebarProvider.openNavigation',
	'toast.dismiss',
];

describe('C4 dictionaries', () => {
	it('should provide the scoped keys for every translated locale', () => {
		for (const locale of ['de', 'es', 'fr'] as const) {
			const dictionary = getC4Dictionary(locale);

			expect(Object.keys(dictionary ?? {}).sort()).toEqual([...EXPECTED_KEYS].sort());
			expect(Object.values(dictionary ?? {}).every((value) => value.length > 0 && !value.includes('{{'))).toBe(true);
		}
	});

	it('should use the design-system defaults for English', () => {
		expect(getC4Dictionary('en')).toBeUndefined();
	});

	it('should resolve supported regional locales and fall back to English', () => {
		expect(getC4Locale('de-DE')).toBe('de');
		expect(getC4Locale('fr-FR')).toBe('fr');
		expect(getC4Locale('pt-BR')).toBe('en');
		expect(getC4Locale(undefined)).toBe('en');
	});
});
