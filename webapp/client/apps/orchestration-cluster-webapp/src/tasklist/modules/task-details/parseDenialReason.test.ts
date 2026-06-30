/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {describe, expect} from 'vitest';
import {parseDenialReason} from './parseDenialReason';

function createProblemDetail(overrides: {status?: number; detail?: string} = {}) {
	return {
		type: 'about:blank',
		title: 'CONFLICT',
		status: overrides.status ?? 409,
		detail: overrides.detail ?? 'Command rejected',
		instance: '/v2/user-tasks/123/assignment',
	};
}

describe('parseDenialReason', () => {
	it('should extract denial reason from a 409 problem-detail response', () => {
		const payload = createProblemDetail({
			detail: "Command rejected: Reason to deny: 'User not in candidate list'",
		});

		expect(parseDenialReason(payload, 'assignment')).toBe('User not in candidate list');
	});

	it('should strip double quotes from extracted reason', () => {
		const payload = createProblemDetail({
			detail: 'Command rejected: Reason to deny: "Not allowed"',
		});

		expect(parseDenialReason(payload, 'assignment')).toBe('Not allowed');
	});

	it('should strip single quotes from extracted reason', () => {
		const payload = createProblemDetail({
			detail: "Command rejected: Reason to deny: 'Cannot assign'",
		});

		expect(parseDenialReason(payload, 'unassignment')).toBe('Cannot assign');
	});

	it('should return reason without quotes when no surrounding quotes', () => {
		const payload = createProblemDetail({
			detail: 'Command rejected: Reason to deny: Task is locked',
		});

		expect(parseDenialReason(payload, 'assignment')).toBe('Task is locked');
	});

	it('should return undefined', () => {
		expect(
			parseDenialReason(createProblemDetail({status: 400, detail: "Reason to deny: 'some reason'"}), 'assignment'),
		).toBeUndefined();
		expect(
			parseDenialReason(createProblemDetail({status: 500, detail: "Reason to deny: 'error'"}), 'assignment'),
		).toBeUndefined();
		expect(parseDenialReason({invalid: true}, 'assignment')).toBeUndefined();
		expect(parseDenialReason(undefined, 'assignment')).toBeUndefined();
		expect(parseDenialReason(null, 'assignment')).toBeUndefined();
	});

	it('should return generic fallback when detail has no "Reason to deny:" pattern', () => {
		const payload = createProblemDetail({detail: 'Task assignment conflict without reason'});

		expect(parseDenialReason(payload, 'assignment')).toBe('The task assignment was rejected by the system.');
	});

	it('should work with completion type', () => {
		const payload = createProblemDetail({
			detail: "Command rejected: Reason to deny: 'Task not completable'",
		});

		expect(parseDenialReason(payload, 'completion')).toBe('Task not completable');
	});
});
