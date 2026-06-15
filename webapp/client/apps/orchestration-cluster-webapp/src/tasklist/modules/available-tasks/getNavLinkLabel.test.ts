/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {getNavLinkLabel} from './getNavLinkLabel';

describe('getNavLinkLabel', () => {
	it('should return the "assigned to me" label', () => {
		const result = getNavLinkLabel({
			displayName: 'Review invoice',
			assigneeId: 'demo',
			currentUsername: 'demo',
		});

		expect(result).toBe('Task assigned to me: Review invoice');
	});

	it('should return the "assigned task" label', () => {
		const result = getNavLinkLabel({
			displayName: 'Review invoice',
			assigneeId: 'other-user',
			currentUsername: 'demo',
		});

		expect(result).toBe('Assigned task: Review invoice');
	});

	it('should return the "unassigned task" label for a null assignee', () => {
		const result = getNavLinkLabel({
			displayName: 'Review invoice',
			assigneeId: null,
			currentUsername: 'demo',
		});

		expect(result).toBe('Unassigned task: Review invoice');
	});

	it('should return the "unassigned task" label for an undefined assignee', () => {
		const result = getNavLinkLabel({
			displayName: 'Review invoice',
			assigneeId: undefined,
			currentUsername: 'demo',
		});

		expect(result).toBe('Unassigned task: Review invoice');
	});

	it('should include the task display name in every label variant', () => {
		const name = 'Approve purchase order';

		expect(getNavLinkLabel({displayName: name, assigneeId: 'demo', currentUsername: 'demo'})).toContain(name);
		expect(getNavLinkLabel({displayName: name, assigneeId: 'other', currentUsername: 'demo'})).toContain(name);
		expect(getNavLinkLabel({displayName: name, assigneeId: null, currentUsername: 'demo'})).toContain(name);
		expect(getNavLinkLabel({displayName: name, assigneeId: undefined, currentUsername: 'demo'})).toContain(name);
	});
});
