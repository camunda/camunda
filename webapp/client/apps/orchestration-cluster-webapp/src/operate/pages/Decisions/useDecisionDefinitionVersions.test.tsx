/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {queryDecisionDefinitionsRequestBodySchema} from '@camunda/camunda-api-zod-schemas/8.11';
import {describe, expect, vi} from 'vitest';
import {cleanup, render} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {mockQueryDecisionDefinitionsEndpoint} from '#/shared-test-modules/mock-handlers';
import {
	createDecisionDefinition,
	createQueryDecisionDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/decision-definitions';
import {useDecisionDefinitionVersions} from './useDecisionDefinitionVersions';

function Versions() {
	const {data, isFetching, hasNextPage} = useDecisionDefinitionVersions('invoice-approval', 'tenant-a');

	return <output>{`${isFetching || hasNextPage ? 'Loading' : 'Complete'}: ${data?.length ?? 0} versions`}</output>;
}

describe('useDecisionDefinitionVersions', () => {
	it.for([
		{
			description: 'an exact total on a short page',
			firstCount: 1,
			totalItems: 1,
			hasMoreTotalItems: false,
			expectedRequests: 1,
		},
		{
			description: 'an exact total on a full page',
			firstCount: 1000,
			totalItems: 1000,
			hasMoreTotalItems: false,
			expectedRequests: 1,
		},
		{
			description: 'an exact total across multiple pages',
			firstCount: 1000,
			secondCount: 1,
			totalItems: 1001,
			hasMoreTotalItems: false,
			expectedRequests: 2,
		},
		{
			description: 'a capped total across multiple pages',
			firstCount: 1,
			secondCount: 1,
			totalItems: 1,
			hasMoreTotalItems: true,
			endCursor: null,
			expectedRequests: 2,
		},
		{
			description: 'a missing cursor with more reported items',
			firstCount: 1,
			totalItems: 2,
			hasMoreTotalItems: false,
			firstCursor: null,
			expectedRequests: 1,
		},
		{
			description: 'a repeated cursor with a capped total',
			firstCount: 1,
			secondCount: 1,
			totalItems: 1,
			hasMoreTotalItems: true,
			endCursor: 'first-page',
			expectedRequests: 2,
		},
		{
			description: 'an empty page carrying a cursor',
			firstCount: 0,
			totalItems: 1,
			hasMoreTotalItems: true,
			expectedRequests: 1,
		},
	])(
		'should avoid an extra request after loading $description',
		async (
			{
				firstCount,
				secondCount = 0,
				totalItems,
				hasMoreTotalItems,
				firstCursor = 'first-page',
				endCursor = 'last-page',
				expectedRequests,
			},
			{worker},
		) => {
			const onRequest = vi.fn();
			const schema = queryDecisionDefinitionsRequestBodySchema.transform((body) => {
				onRequest(body);
				return body;
			});
			const failureResponse = new HttpResponse(null, {status: 400});
			worker.use(
				mockQueryDecisionDefinitionsEndpoint({
					once: true,
					schema,
					successResponse: HttpResponse.json(
						createQueryDecisionDefinitionsResponse({
							items: Array.from({length: firstCount}, (_, index) =>
								createDecisionDefinition({
									decisionDefinitionKey: String(index),
									version: index + 1,
								}),
							),
							page: {totalItems, hasMoreTotalItems, endCursor: firstCursor},
						}),
					),
					failureResponse,
				}),
				mockQueryDecisionDefinitionsEndpoint({
					schema,
					successResponse: HttpResponse.json(
						createQueryDecisionDefinitionsResponse({
							items: Array.from({length: secondCount}, (_, index) =>
								createDecisionDefinition({
									decisionDefinitionKey: String(firstCount + index),
									version: firstCount + index + 1,
								}),
							),
							page: {totalItems, hasMoreTotalItems, endCursor},
						}),
					),
					failureResponse,
				}),
			);
			const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});

			try {
				const screen = await render(
					<QueryClientProvider client={queryClient}>
						<Versions />
					</QueryClientProvider>,
				);

				await expect
					.element(screen.getByRole('status'))
					.toHaveTextContent(`Complete: ${firstCount + secondCount} versions`);
				expect(onRequest).toHaveBeenCalledTimes(expectedRequests);
				expect(onRequest).toHaveBeenNthCalledWith(1, {
					filter: {decisionDefinitionId: 'invoice-approval', tenantId: 'tenant-a'},
					sort: [{field: 'version', order: 'DESC'}],
					page: {limit: 1000},
				});
				if (expectedRequests === 2) {
					expect(onRequest).toHaveBeenNthCalledWith(2, {
						filter: {decisionDefinitionId: 'invoice-approval', tenantId: 'tenant-a'},
						sort: [{field: 'version', order: 'DESC'}],
						page: {after: 'first-page', limit: 1000},
					});
				}
			} finally {
				await cleanup();
				queryClient.clear();
			}
		},
	);
});
