/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient} from '@tanstack/react-query';
import {HttpResponse} from 'msw';
import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {createQueryDecisionDefinitionsResponse} from '#/shared-test-modules/api-mocks/decision-definitions';
import {mockQueryDecisionDefinitionsEndpointByFilter} from '../mockQueryDecisionDefinitionsEndpointByFilter';
import {notificationsStore} from '#/shared/notifications/notifications.store';
import {decisionsRouteLoader} from './decisionsRouteLoader';

function mockEmptyDefinitions() {
	const empty = HttpResponse.json(createQueryDecisionDefinitionsResponse());
	return mockQueryDecisionDefinitionsEndpointByFilter({unfilteredResponse: empty, filteredResponse: empty});
}

function runLoader(preload: boolean) {
	const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});

	return decisionsRouteLoader({queryClient, decisionDefinitionId: 'missing-decision', preload}).catch(
		(error: unknown) => error,
	);
}

describe('decisionsRouteLoader', () => {
	it('should show the not-found notification when navigating to an unknown decision', async ({worker}) => {
		notificationsStore.reset();
		worker.use(mockEmptyDefinitions());
		await runLoader(false);

		expect(notificationsStore.notifications).toEqual([
			expect.objectContaining({kind: 'error', title: "Couldn't find the decision"}),
		]);
	});

	it('should not show the not-found notification when only preloading an unknown decision', async ({worker}) => {
		notificationsStore.reset();
		worker.use(mockEmptyDefinitions());
		await runLoader(true);

		expect(notificationsStore.notifications).toEqual([]);
	});
});
