/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import type {GlobalTaskListenerEventType} from '@camunda/camunda-api-zod-schemas/8.11';
import {syncAllEventType, toFormEventTypes, toRequestEventTypes} from './eventTypes';

const ALL_SELECTED: GlobalTaskListenerEventType[] = [
	'all',
	'creating',
	'assigning',
	'updating',
	'completing',
	'canceling',
];

describe('syncAllEventType', () => {
	it('should select every event type when "all" is checked', () => {
		expect(syncAllEventType(['creating', 'all'], ['creating'])).toEqual(ALL_SELECTED);
	});

	it('should clear every event type when "all" is unchecked', () => {
		expect(
			syncAllEventType(
				ALL_SELECTED.filter((eventType) => eventType !== 'all'),
				ALL_SELECTED,
			),
		).toEqual([]);
	});

	it('should uncheck "all" and keep the rest when one individual event is deselected', () => {
		expect(
			syncAllEventType(
				ALL_SELECTED.filter((eventType) => eventType !== 'updating'),
				ALL_SELECTED,
			),
		).toEqual(['creating', 'assigning', 'completing', 'canceling']);
	});

	it('should check "all" when the last individual event is selected', () => {
		expect(
			syncAllEventType(
				['creating', 'assigning', 'updating', 'completing', 'canceling'],
				['creating', 'assigning', 'updating', 'completing'],
			),
		).toEqual(ALL_SELECTED);
	});

	it('should restore the schema order regardless of selection order', () => {
		expect(syncAllEventType(['completing', 'creating'], ['completing'])).toEqual(['creating', 'completing']);
	});
});

describe('toRequestEventTypes', () => {
	it('should send "all" on its own when it is selected', () => {
		expect(toRequestEventTypes(ALL_SELECTED)).toEqual(['all']);
	});

	it('should send the individual event types otherwise', () => {
		expect(toRequestEventTypes(['creating', 'updating'])).toEqual(['creating', 'updating']);
	});
});

describe('toFormEventTypes', () => {
	it('should expand "all" into every event type', () => {
		expect(toFormEventTypes(['all'])).toEqual(ALL_SELECTED);
	});

	it('should keep individual event types in schema order', () => {
		expect(toFormEventTypes(['updating', 'creating'])).toEqual(['creating', 'updating']);
	});
});
