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
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {loadDecisionsData} from './loadDecisionsData';
import {mockQueryDecisionDefinitionsEndpointByFilter} from './mockQueryDecisionDefinitionsEndpointByFilter';

const EMPTY_RESPONSE = HttpResponse.json(createQueryDecisionDefinitionsResponse());

describe('loadDecisionsData', () => {
	it('returns false when the selected decision does not exist', async ({worker}) => {
		worker.use(
			mockQueryDecisionDefinitionsEndpointByFilter({
				unfilteredResponse: EMPTY_RESPONSE,
				filteredResponse: EMPTY_RESPONSE,
			}),
		);
		const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});

		const isSelectionValid = await loadDecisionsData({
			queryClient,
			decisionDefinitionId: 'missing-decision',
		});

		expect(isSelectionValid).toBe(false);
	});

	it('refetches a previously missing selection', async ({worker}) => {
		worker.use(
			mockQueryDecisionDefinitionsEndpointByFilter({
				unfilteredResponse: EMPTY_RESPONSE,
				filteredResponse: EMPTY_RESPONSE,
			}),
		);
		const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
		const options = {
			queryClient,
			decisionDefinitionId: 'new-decision',
			decisionDefinitionVersion: 1,
		};
		await expect(loadDecisionsData(options)).resolves.toBe(false);

		const definition = createDecisionDefinition({
			decisionDefinitionId: 'new-decision',
			version: 1,
		});
		worker.use(
			mockQueryDecisionDefinitionsEndpointByFilter({
				unfilteredResponse: EMPTY_RESPONSE,
				filteredResponse: HttpResponse.json(createQueryDecisionDefinitionsResponse({items: [definition]})),
			}),
		);

		await expect(loadDecisionsData(options)).resolves.toBe(true);
	});
});
