/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

type Validators = Record<string, z.ZodType>;

function createBrowserStorage<V extends Validators>(storage: Storage, validators: V) {
	function store<Key extends keyof V & string>(key: Key, value: z.infer<V[Key]>) {
		storage.setItem(key, JSON.stringify(value));
	}

	function get<Key extends keyof V & string>(key: Key): z.infer<V[Key]> | null {
		try {
			const raw = storage.getItem(key);

			if (raw === null) {
				return null;
			}

			const result = validators[key]!.safeParse(JSON.parse(raw));

			return result.success ? (result.data as z.infer<V[Key]>) : null;
		} catch {
			return null;
		}
	}

	function clear(key: keyof V & string) {
		storage.removeItem(key);
	}

	return {store, get, clear};
}

export {createBrowserStorage};
