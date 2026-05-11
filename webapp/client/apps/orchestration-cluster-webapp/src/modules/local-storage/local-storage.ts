/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

const validators = {
	theme: z.enum(['light', 'dark', 'system']),
} as const;

type Validators = typeof validators;
type StorageKey = keyof Validators;

function storeStateLocally<Key extends StorageKey>(storageKey: Key, value: z.infer<Validators[Key]>) {
	localStorage.setItem(storageKey, JSON.stringify(value));
}

function clearStateLocally(storageKey: StorageKey) {
	localStorage.removeItem(storageKey);
}

function getStateLocally<Key extends StorageKey>(storageKey: Key) {
	try {
		const value = localStorage.getItem(storageKey);

		if (value === null) {
			return null;
		}

		const result = validators[storageKey].safeParse(JSON.parse(value));

		return result.success ? (result.data as z.infer<Validators[Key]>) : null;
	} catch {
		return null;
	}
}

export {storeStateLocally, clearStateLocally, getStateLocally};
