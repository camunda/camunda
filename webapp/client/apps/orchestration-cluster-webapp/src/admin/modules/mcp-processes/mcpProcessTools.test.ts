/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import type {MessageSubscription} from '@camunda/camunda-api-zod-schemas/8.10';
import {getMcpProcessToolsRequestBody, mapResponseToTools} from './mcpProcessTools';

function createSubscription(overrides: Partial<MessageSubscription> = {}): MessageSubscription {
	return {
		messageSubscriptionKey: '2251799813685249',
		processDefinitionId: 'order-process',
		processDefinitionKey: '2251799813685248',
		processInstanceKey: null,
		elementId: 'StartEvent_1',
		elementInstanceKey: null,
		messageSubscriptionState: 'CREATED',
		messageSubscriptionType: 'START_EVENT',
		lastUpdatedDate: '2026-09-23T10:00:00.000Z',
		messageName: 'order-received',
		correlationKey: null,
		tenantId: '<default>',
		rootProcessInstanceKey: null,
		toolProperties: {},
		processDefinitionName: 'Order process',
		processDefinitionVersion: 3,
		toolName: 'place-order',
		inboundConnectorType: null,
		...overrides,
	};
}

function createResponse(items: MessageSubscription[]) {
	return {
		items,
		page: {totalItems: items.length, startCursor: null, endCursor: null, hasMoreTotalItems: false},
	};
}

describe('mapResponseToTools', () => {
	it('should unpack the namespaced tool properties', () => {
		// given
		const response = createResponse([
			createSubscription({
				toolProperties: {
					'io.camunda.tool:purpose': 'Places an order',
					'io.camunda.tool:results': 'An order confirmation',
					'io.camunda.tool:when_to_use': 'When the cart is ready',
					'io.camunda.tool:when_not_to_use': 'When payment is pending',
				},
			}),
		]);

		// when
		const [tool] = mapResponseToTools(response);

		// then
		expect(tool?.toolProperties).toEqual({
			purpose: 'Places an order',
			results: 'An order confirmation',
			whenToUse: 'When the cart is ready',
			whenNotToUse: 'When payment is pending',
		});
	});

	it('should report a missing tool property as null rather than an empty string', () => {
		// given
		const response = createResponse([
			createSubscription({toolProperties: {'io.camunda.tool:purpose': '   ', 'io.camunda.tool:results': 'Some'}}),
		]);

		// when
		const [tool] = mapResponseToTools(response);

		// then
		expect(tool?.toolProperties).toEqual({
			purpose: null,
			results: 'Some',
			whenToUse: null,
			whenNotToUse: null,
		});
	});

	it('should drop subscriptions that carry no tool name', () => {
		// given
		const response = createResponse([
			createSubscription({messageSubscriptionKey: '1', toolName: null}),
			createSubscription({messageSubscriptionKey: '2', toolName: 'place-order'}),
		]);

		// when
		const tools = mapResponseToTools(response);

		// then
		expect(tools.map(({id}) => id)).toEqual(['2']);
	});

	it('should fall back to the process definition id when the process has no name', () => {
		// given
		const response = createResponse([
			createSubscription({processDefinitionName: null, processDefinitionId: 'order-process'}),
		]);

		// when
		const [tool] = mapResponseToTools(response);

		// then
		expect(tool?.processDefinitionName).toBe('order-process');
	});
});

describe('getMcpProcessToolsRequestBody', () => {
	it('should request only start-event subscriptions that expose a tool', () => {
		// when
		const body = getMcpProcessToolsRequestBody({});

		// then
		expect(body.filter).toEqual({
			messageSubscriptionType: 'START_EVENT',
			messageSubscriptionState: {$neq: 'DELETED'},
			toolName: {$exists: true},
		});
	});

	it('should sort by tool name ascending by default', () => {
		// when
		const body = getMcpProcessToolsRequestBody({});

		// then
		expect(body.sort).toEqual([{field: 'toolName', order: 'asc'}]);
	});

	it('should sort descending when the search state asks for it', () => {
		// when
		const body = getMcpProcessToolsRequestBody({sortOrder: 'desc'});

		// then
		expect(body.sort).toEqual([{field: 'toolName', order: 'desc'}]);
	});

	it('should turn a search term into a contains filter on the tool name', () => {
		// when
		const body = getMcpProcessToolsRequestBody({search: 'order'});

		// then
		expect(body.filter?.toolName).toEqual({$exists: true, $like: '*order*'});
	});

	it('should ignore a search term that is only whitespace', () => {
		// when
		const body = getMcpProcessToolsRequestBody({search: '   '});

		// then
		expect(body.filter?.toolName).toEqual({$exists: true});
	});

	it('should request the first page by default', () => {
		// when
		const body = getMcpProcessToolsRequestBody({});

		// then
		expect(body.page).toEqual({from: 0, limit: 20});
	});

	it('should offset the request by the requested page', () => {
		// when
		const body = getMcpProcessToolsRequestBody({page: 3, pageSize: 50});

		// then
		expect(body.page).toEqual({from: 100, limit: 50});
	});
});
