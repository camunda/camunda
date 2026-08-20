/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {getPriorityLabel} from './getPriorityLabel';

describe('getPriorityLabel', () => {
	it('should return key "critical" for priority > 75', () => {
		expect(getPriorityLabel(100).key).toBe('critical');
		expect(getPriorityLabel(80).key).toBe('critical');
		expect(getPriorityLabel(76).key).toBe('critical');
	});

	it('should return key "high" for priority 51-75', () => {
		expect(getPriorityLabel(75).key).toBe('high');
		expect(getPriorityLabel(60).key).toBe('high');
		expect(getPriorityLabel(51).key).toBe('high');
	});

	it('should return key "medium" for priority 26-50', () => {
		expect(getPriorityLabel(50).key).toBe('medium');
		expect(getPriorityLabel(30).key).toBe('medium');
		expect(getPriorityLabel(26).key).toBe('medium');
	});

	it('should return key "low" for priority 0-25', () => {
		expect(getPriorityLabel(25).key).toBe('low');
		expect(getPriorityLabel(20).key).toBe('low');
		expect(getPriorityLabel(0).key).toBe('low');
	});

	it('should return the correct short label for each bucket', () => {
		expect(getPriorityLabel(80).short).toBe('Critical');
		expect(getPriorityLabel(60).short).toBe('High');
		expect(getPriorityLabel(30).short).toBe('Medium');
		expect(getPriorityLabel(20).short).toBe('Low');
	});

	it('should return the correct long label for each bucket', () => {
		expect(getPriorityLabel(80).long).toBe('Priority: Critical');
		expect(getPriorityLabel(60).long).toBe('Priority: High');
		expect(getPriorityLabel(30).long).toBe('Priority: Medium');
		expect(getPriorityLabel(20).long).toBe('Priority: Low');
	});

	it('should handle exact boundary values correctly', () => {
		expect(getPriorityLabel(75).key).toBe('high');
		expect(getPriorityLabel(50).key).toBe('medium');
		expect(getPriorityLabel(25).key).toBe('low');
	});
});
