/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {z} from 'zod';
import {createBrowserStorage} from './createBrowserStorage';

function createMockStorage(): Storage {
	let store: Record<string, string> = {};

	return {
		get length() {
			return Object.keys(store).length;
		},
		key(index: number) {
			return Object.keys(store)[index] ?? null;
		},
		getItem(key: string) {
			return store[key] ?? null;
		},
		setItem(key: string, value: string) {
			store[key] = value;
		},
		removeItem(key: string) {
			delete store[key];
		},
		clear() {
			store = {};
		},
	};
}

const validators = {
	name: z.string(),
	count: z.number().int(),
	active: z.boolean(),
};

describe('createBrowserStorage', () => {
	it('should store and retrieve a string value', () => {
		const browserStorage = createBrowserStorage(createMockStorage(), validators);

		browserStorage.store('name', 'Alice');

		expect(browserStorage.get('name')).toBe('Alice');
	});

	it('should return null when no value exists', () => {
		const browserStorage = createBrowserStorage(createMockStorage(), validators);

		expect(browserStorage.get('name')).toBeNull();
	});

	it('should clear a stored value', () => {
		const browserStorage = createBrowserStorage(createMockStorage(), validators);

		browserStorage.store('name', 'Alice');
		browserStorage.store('count', 10);

		browserStorage.clear('name');

		expect(browserStorage.get('name')).toBeNull();
		expect(browserStorage.get('count')).toBe(10);
	});

	it('should return null for malformed JSON', () => {
		const mockStorage = createMockStorage();
		const browserStorage = createBrowserStorage(mockStorage, validators);

		mockStorage.setItem('name', '{invalid json');

		expect(browserStorage.get('name')).toBeNull();
	});

	it('should return null when stored value fails validation', () => {
		const mockStorage = createMockStorage();
		const browserStorage = createBrowserStorage(mockStorage, validators);

		mockStorage.setItem('count', JSON.stringify('not-a-number'));

		expect(browserStorage.get('count')).toBeNull();
	});

	it('should serialize values as JSON in the underlying storage', () => {
		const mockStorage = createMockStorage();
		const browserStorage = createBrowserStorage(mockStorage, validators);

		browserStorage.store('name', 'Bob');

		expect(mockStorage.getItem('name')).toBe('"Bob"');
	});

	it('should return null for a non-JSON string', () => {
		const mockStorage = createMockStorage();
		const browserStorage = createBrowserStorage(mockStorage, validators);

		mockStorage.setItem('active', 'yes');

		expect(browserStorage.get('active')).toBeNull();
	});
});
