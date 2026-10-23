/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {HttpResponse, delay, http} from 'msw';
import {endpoints} from '@camunda/camunda-api-zod-schemas/8.11';
import {cleanup, renderHook} from 'vitest-browser-react';
import {afterEach, describe, expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {mockQueryDecisionDefinitionsEndpointByFilter} from '../mockQueryDecisionDefinitionsEndpointByFilter';
import {useDecisionDefinitionSelection} from './useDecisionDefinitionSelection';

describe('useDecisionDefinitionSelection', () => {
	afterEach(async () => {
		await cleanup();
	});

	it('should not report loading during a background refetch of the selected definition', async ({worker}) => {
		// given
		const definition = createDecisionDefinition({decisionDefinitionId: 'invoice-classification', version: 1});
		const response = createQueryDecisionDefinitionsResponse({items: [definition]});
		worker.use(
			mockQueryDecisionDefinitionsEndpointByFilter({
				unfilteredResponse: HttpResponse.json(response),
				filteredResponse: HttpResponse.json(response),
			}),
		);
		const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
		const loadingStates: boolean[] = [];
		const {result} = await renderHook(
			() => {
				const selection = useDecisionDefinitionSelection({
					decisionDefinitionId: 'invoice-classification',
					decisionDefinitionVersion: 1,
				});
				loadingStates.push(selection.isDefinitionSelectionLoading);
				return selection;
			},
			{wrapper: ({children}) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>},
		);
		await vi.waitFor(() => expect(result.current.decisionDefinitionSelection.kind).toBe('single-version'));
		worker.use(
			http.post(endpoints.queryDecisionDefinitions.getUrl(), async () => {
				await delay(300);
				return HttpResponse.json(response);
			}),
		);
		const statesBeforeRefetch = loadingStates.length;

		// when
		await queryClient.invalidateQueries();

		// then
		expect(loadingStates.slice(statesBeforeRefetch)).not.toContain(true);
	});
});
