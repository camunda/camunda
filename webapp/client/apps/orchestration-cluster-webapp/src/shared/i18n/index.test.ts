/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect, beforeEach} from 'vitest';
import i18n from 'i18next';
import {enUS, fr, de, es} from 'date-fns/locale';
import {languageItems, translationResources, getCurrentDateLocale} from '.';
import enTranslation from './locales/en.json';
import frTranslation from './locales/fr.json';
import deTranslation from './locales/de.json';
import esTranslation from './locales/es.json';
import '#/vitest-modules/i18n-setup';

describe('i18n module', () => {
	beforeEach(async () => {
		await i18n.changeLanguage('en');
	});

	it('should contain all supported languages', () => {
		expect(languageItems).toHaveLength(4);
		expect(languageItems).toEqual([
			{id: 'en', label: 'English'},
			{id: 'fr', label: 'Français'},
			{id: 'de', label: 'Deutsch'},
			{id: 'es', label: 'Español'},
		]);
	});

	it('should contain resources for all supported languages', () => {
		expect(Object.keys(translationResources)).toEqual(['en', 'fr', 'de', 'es']);
	});

	it.for([
		{language: 'fr', locale: frTranslation},
		{language: 'de', locale: deTranslation},
		{language: 'es', locale: esTranslation},
	])('should define every English operate key in $language', ({locale}) => {
		const flattenKeys = (node: object, prefix = ''): string[] =>
			Object.entries(node).flatMap(([key, value]) =>
				typeof value === 'object' ? flattenKeys(value, `${prefix}${key}.`) : [`${prefix}${key}`],
			);
		const operateKeys = (file: {translation: {operate: object}}) => flattenKeys(file.translation.operate).sort();

		expect(operateKeys(locale)).toEqual(operateKeys(enTranslation));
	});

	it('should include translation keys in each resource', () => {
		expect(translationResources).toMatchObject({
			en: {
				translation: {
					loginButtonLabel: 'Login',
					loginErrorUsernameRequired: 'Username is required',
					loginErrorPasswordRequired: 'Password is required',
				},
			},
		});
	});

	it('should return enUS locale for English', () => {
		expect(getCurrentDateLocale()).toBe(enUS);
	});

	it('should return fr locale for French', async () => {
		await i18n.changeLanguage('fr');
		expect(getCurrentDateLocale()).toBe(fr);
	});

	it('should return de locale for German', async () => {
		await i18n.changeLanguage('de');
		expect(getCurrentDateLocale()).toBe(de);
	});

	it('should return es locale for Spanish', async () => {
		await i18n.changeLanguage('es');
		expect(getCurrentDateLocale()).toBe(es);
	});

	it('should fall back to enUS for unknown language', async () => {
		await i18n.changeLanguage('unknown');
		expect(getCurrentDateLocale()).toBe(enUS);
	});
});
