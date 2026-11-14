/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect} from 'vitest';
import translationsEn from './locales/en.json';
import translationsFr from './locales/fr.json';
import translationsDe from './locales/de.json';
import translationsEs from './locales/es.json';

const sources = import.meta.glob<string>(
	['/src/**/*.{ts,tsx}', '!/src/**/*.test.{ts,tsx}', '!/src/shared/i18n/locales/**'],
	{
		query: '?raw',
		import: 'default',
		eager: true,
	},
);

const LOCALES = {fr: translationsFr, de: translationsDe, es: translationsEs};
const STATIC_KEY_PATTERN = /\bt\(\s*'(operate\.[A-Za-z0-9_.]+)'/g;
const PLURAL_SUFFIX_PATTERN = /_(zero|one|two|few|many|other)$/;

function flattenKeys(value: unknown, prefix = ''): string[] {
	if (typeof value !== 'object' || value === null) {
		return [prefix];
	}
	return Object.entries(value).flatMap(([key, child]) => flattenKeys(child, prefix === '' ? key : `${prefix}.${key}`));
}

function withoutPluralSuffix(key: string) {
	return key.replace(PLURAL_SUFFIX_PATTERN, '');
}

describe('translation keys', () => {
	it('should define every statically referenced operate key in the English locale', () => {
		// given
		const definedKeys = new Set(flattenKeys(translationsEn.translation).map(withoutPluralSuffix));
		const referencedKeys = Object.values(sources).flatMap((source) =>
			Array.from(source.matchAll(STATIC_KEY_PATTERN), (match) => match[1] ?? ''),
		);

		// when
		const missingKeys = [...new Set(referencedKeys)].filter((key) => !definedKeys.has(key));

		// then
		expect(missingKeys).toEqual([]);
	});

	it.for(['fr', 'de', 'es'] as const)('should define every English operate key in the %s locale', (language) => {
		// given
		const operateKeys = (keys: string[]) => keys.filter((key) => key.startsWith('operate.'));
		const englishKeys = operateKeys(flattenKeys(translationsEn.translation));
		const localeKeys = new Set(operateKeys(flattenKeys(LOCALES[language].translation)));

		// when
		const missingKeys = englishKeys.filter((key) => !localeKeys.has(key));

		// then
		expect(missingKeys).toEqual([]);
	});
});
