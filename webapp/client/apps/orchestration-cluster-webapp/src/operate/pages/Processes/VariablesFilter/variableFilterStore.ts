/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useSyncExternalStore} from 'react';
import {z} from 'zod';
import {createBrowserStorage} from '#/shared/browser-storage/createBrowserStorage';
import {variableConditionSchema, type VariableCondition} from './variableConditions';

const STORAGE_KEY = 'operate.variableFilter.conditions';

const storage = createBrowserStorage(sessionStorage, {
	[STORAGE_KEY]: z.array(z.unknown()).transform((entries) =>
		entries.flatMap((entry) => {
			const result = variableConditionSchema.safeParse(entry);
			return result.success ? [result.data] : [];
		}),
	),
});

const listeners = new Set<() => void>();
let snapshot: {raw: string | null; conditions: VariableCondition[]} | undefined;

function getVariableConditions(): VariableCondition[] {
	const raw = sessionStorage.getItem(STORAGE_KEY);
	if (snapshot === undefined || snapshot.raw !== raw) {
		snapshot = {raw, conditions: storage.get(STORAGE_KEY) ?? []};
	}
	return snapshot.conditions;
}

function setVariableConditions(conditions: VariableCondition[]) {
	if (JSON.stringify(conditions) === JSON.stringify(getVariableConditions())) {
		return;
	}
	if (conditions.length === 0) {
		storage.clear(STORAGE_KEY);
	} else {
		storage.store(STORAGE_KEY, conditions);
	}
	listeners.forEach((listener) => listener());
}

function subscribe(listener: () => void) {
	listeners.add(listener);
	return () => {
		listeners.delete(listener);
	};
}

function useVariableConditions() {
	return useSyncExternalStore(subscribe, getVariableConditions);
}

export {useVariableConditions, setVariableConditions};
