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
import '#/vitest-modules/i18n-setup';

describe('i18n module', () => {
	describe('languageItems', () => {
		it('should contain all supported languages', () => {
			expect(languageItems).toHaveLength(4);
			expect(languageItems).toEqual([
				{id: 'en', label: 'English'},
				{id: 'fr', label: 'Français'},
				{id: 'de', label: 'Deutsch'},
				{id: 'es', label: 'Español'},
			]);
		});
	});

	describe('translationResources', () => {
		it('should contain resources for all supported languages', () => {
			expect(Object.keys(translationResources)).toEqual(['en', 'fr', 'de', 'es']);
		});

		it('should include translation keys in each resource', () => {
			expect((translationResources['en'] as {translation: Record<string, string>}).translation).toMatchObject({
				loginButtonLabel: 'Login',
				loginErrorUsernameRequired: 'Username is required',
				loginErrorPasswordRequired: 'Password is required',
			});
		});
	});

	describe('getCurrentDateLocale', () => {
		beforeEach(() => {
			i18n.changeLanguage('en');
		});

		it('should return enUS locale for English', () => {
			i18n.changeLanguage('en');
			expect(getCurrentDateLocale()).toBe(enUS);
		});

		it('should return fr locale for French', () => {
			i18n.changeLanguage('fr');
			expect(getCurrentDateLocale()).toBe(fr);
		});

		it('should return de locale for German', () => {
			i18n.changeLanguage('de');
			expect(getCurrentDateLocale()).toBe(de);
		});

		it('should return es locale for Spanish', () => {
			i18n.changeLanguage('es');
			expect(getCurrentDateLocale()).toBe(es);
		});

		it('should fall back to enUS for unknown language', () => {
			i18n.changeLanguage('unknown');
			expect(getCurrentDateLocale()).toBe(enUS);
		});
	});
});
