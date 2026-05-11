/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {storeStateLocally, getStateLocally, clearStateLocally} from './local-storage';
import {it} from '#/vitest-modules/test-extend';
import {beforeEach, describe, expect} from 'vitest';

const KEY_NAME = 'theme';
const VALID_THEME_VALUES = ['light', 'dark', 'system'] as const;
const INVALID_STORED_VALUES = ['blue', null, {}, [], 123] as const;

describe('localStorage', () => {
	beforeEach(() => {
		localStorage.clear();
	});

	it.for(VALID_THEME_VALUES)('should store and retrieve the %s theme', (theme) => {
		storeStateLocally(KEY_NAME, theme);

		expect(localStorage.getItem(KEY_NAME)).toEqual(JSON.stringify(theme));
		expect(getStateLocally(KEY_NAME)).toEqual(theme);
	});

	it('should return null when no value exists', () => {
		expect(getStateLocally(KEY_NAME)).toBeNull();
	});

	it('should clear stored state', () => {
		const unrelatedKey = 'unrelated';

		storeStateLocally(KEY_NAME, 'dark');
		localStorage.setItem(unrelatedKey, 'value');

		clearStateLocally(KEY_NAME);

		expect(getStateLocally(KEY_NAME)).toBeNull();
		expect(localStorage.getItem(unrelatedKey)).toEqual('value');
	});

	it('should return null for malformed JSON', () => {
		localStorage.setItem(KEY_NAME, 'dark');

		expect(getStateLocally(KEY_NAME)).toBeNull();
	});

	it.for(INVALID_STORED_VALUES)('should return null for invalid stored value %s', (value) => {
		localStorage.setItem(KEY_NAME, JSON.stringify(value));

		expect(getStateLocally(KEY_NAME)).toBeNull();
	});
});
