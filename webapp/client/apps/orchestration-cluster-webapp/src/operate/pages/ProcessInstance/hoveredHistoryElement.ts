/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const listeners = new Set<() => void>();
const state = {elementId: null as string | null};

const hoveredHistoryElement = {
	subscribe: (listener: () => void) => {
		listeners.add(listener);
		return () => {
			listeners.delete(listener);
		};
	},
	getSnapshot: () => state.elementId,
	set: (elementId: string | null) => {
		state.elementId = elementId;
		listeners.forEach((listener) => listener());
	},
};

export {hoveredHistoryElement};
