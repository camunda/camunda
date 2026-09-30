/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {formatEnumLabel, getAuditLogsRequestBody} from './auditLogs';

describe('getAuditLogsRequestBody', () => {
	it('should always scope the request to the admin audit-log category', () => {
		// when
		const body = getAuditLogsRequestBody({});

		// then
		expect(body.filter?.category).toEqual({$eq: 'ADMIN'});
	});

	it('should sort by timestamp descending by default', () => {
		// when
		const body = getAuditLogsRequestBody({});

		// then
		expect(body.sort).toEqual([{field: 'timestamp', order: 'DESC'}]);
	});

	it('should sort ascending by the requested field when no order is given', () => {
		// when
		const body = getAuditLogsRequestBody({sortField: 'actorId'});

		// then
		expect(body.sort).toEqual([{field: 'actorId', order: 'ASC'}]);
	});

	it('should honor a descending sort on a non-default field', () => {
		// when
		const body = getAuditLogsRequestBody({sortField: 'operationType', sortOrder: 'DESC'});

		// then
		expect(body.sort).toEqual([{field: 'operationType', order: 'DESC'}]);
	});

	it('should request the first page by default', () => {
		// when
		const body = getAuditLogsRequestBody({});

		// then
		expect(body.page).toEqual({from: 0, limit: 50});
	});

	it('should offset the request by the requested page', () => {
		// when
		const body = getAuditLogsRequestBody({page: 3, pageSize: 100});

		// then
		expect(body.page).toEqual({from: 200, limit: 100});
	});

	it('should pass simple filters through untouched', () => {
		// when
		const body = getAuditLogsRequestBody({operationType: 'CREATE', entityType: 'ROLE', result: 'FAIL', actor: 'demo'});

		// then
		expect(body.filter).toMatchObject({
			operationType: 'CREATE',
			entityType: 'ROLE',
			result: 'FAIL',
			actorId: 'demo',
		});
	});

	it('should include the related-entity filters when the entity type is AUTHORIZATION', () => {
		// when
		const body = getAuditLogsRequestBody({
			entityType: 'AUTHORIZATION',
			relatedEntityType: 'USER',
			relatedEntityKey: 'demo',
		});

		// then
		expect(body.filter?.relatedEntityType).toBe('USER');
		expect(body.filter?.relatedEntityKey).toBe('demo');
	});

	it('should drop the related-entity filters when the entity type is not AUTHORIZATION', () => {
		// when
		const body = getAuditLogsRequestBody({
			entityType: 'ROLE',
			relatedEntityType: 'USER',
			relatedEntityKey: 'demo',
		});

		// then
		expect(body.filter?.relatedEntityType).toBeUndefined();
		expect(body.filter?.relatedEntityKey).toBeUndefined();
	});

	it('should drop the related-entity filters when no entity type is selected at all', () => {
		// when
		const body = getAuditLogsRequestBody({relatedEntityType: 'USER', relatedEntityKey: 'demo'});

		// then
		expect(body.filter?.relatedEntityType).toBeUndefined();
		expect(body.filter?.relatedEntityKey).toBeUndefined();
	});

	it('should request a timestamp range only when both bounds are set', () => {
		// when
		const bothBounds = getAuditLogsRequestBody({
			timestampFrom: '2026-01-01T00:00:00.000Z',
			timestampTo: '2026-01-31T00:00:00.000Z',
		});
		const fromOnly = getAuditLogsRequestBody({timestampFrom: '2026-01-01T00:00:00.000Z'});
		const toOnly = getAuditLogsRequestBody({timestampTo: '2026-01-31T00:00:00.000Z'});
		const neither = getAuditLogsRequestBody({});

		// then
		expect(bothBounds.filter?.timestamp).toEqual({
			$gte: '2026-01-01T00:00:00.000Z',
			$lte: '2026-01-31T00:00:00.000Z',
		});
		expect(fromOnly.filter?.timestamp).toBeUndefined();
		expect(toOnly.filter?.timestamp).toBeUndefined();
		expect(neither.filter?.timestamp).toBeUndefined();
	});
});

describe('formatEnumLabel', () => {
	it('should turn a single-word enum value into a capitalized word', () => {
		expect(formatEnumLabel('CREATE')).toBe('Create');
	});

	it('should turn an underscore-separated enum value into a spaced, capitalized label', () => {
		expect(formatEnumLabel('MAPPING_RULE')).toBe('Mapping rule');
	});

	it('should lowercase the remainder of a multi-word value', () => {
		expect(formatEnumLabel('USER_TASK')).toBe('User task');
	});
});
