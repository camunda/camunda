/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {HttpResponse} from 'msw';
import {describe, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import {z} from 'zod';
import {it} from '#/vitest-modules/test-extend';
import {mockQueryDecisionInstancesEndpoint} from '#/shared-test-modules/mock-handlers';
import {
	createDecisionInstance,
	createQueryDecisionInstancesResponse,
} from '#/shared-test-modules/api-mocks/decision-instances';
import {useDrdData} from './drdData.queries';

const EVALUATION_KEY = 'evaluation-123';
const first = createDecisionInstance({
	decisionEvaluationKey: EVALUATION_KEY,
	decisionDefinitionId: 'classification',
	decisionEvaluationInstanceKey: '1',
});
const second = createDecisionInstance({
	decisionEvaluationKey: EVALUATION_KEY,
	decisionDefinitionId: 'approval',
	decisionEvaluationInstanceKey: '2',
});

function DrdDataResult({decisionEvaluationKey}: {decisionEvaluationKey?: string}) {
	const {data, isError, isPending} = useDrdData(decisionEvaluationKey);
	return <pre data-testid="drd-result">{JSON.stringify({data, isError, isPending})}</pre>;
}

async function renderQuery(decisionEvaluationKey?: string) {
	const client = new QueryClient({defaultOptions: {queries: {retry: false}}});
	return render(
		<QueryClientProvider client={client}>
			<DrdDataResult decisionEvaluationKey={decisionEvaluationKey} />
		</QueryClientProvider>,
	);
}

function pageSchema(after?: string) {
	return z.object({
		filter: z.object({decisionEvaluationKey: z.literal(EVALUATION_KEY)}),
		sort: z.tuple([z.object({field: z.literal('decisionEvaluationInstanceKey'), order: z.literal('asc')})]),
		page: z.object({after: after === undefined ? z.undefined().optional() : z.literal(after), limit: z.literal(1000)}),
	});
}

describe('useDrdData', () => {
	it('should map every result in a single-page evaluation family', async ({worker}) => {
		worker.use(
			mockQueryDecisionInstancesEndpoint({
				schema: pageSchema(),
				successResponse: HttpResponse.json(createQueryDecisionInstancesResponse({items: [first, second]})),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		const screen = await renderQuery(EVALUATION_KEY);

		await expect.element(screen.getByTestId('drd-result')).toHaveTextContent(
			JSON.stringify({
				data: {
					classification: {
						decisionDefinitionId: 'classification',
						decisionEvaluationInstanceKey: '1',
						state: 'EVALUATED',
					},
					approval: {
						decisionDefinitionId: 'approval',
						decisionEvaluationInstanceKey: '2',
						state: 'EVALUATED',
					},
				},
				isError: false,
				isPending: false,
			}),
		);
	});

	it('should fetch every page and let the last instance for a definition id win', async ({worker}) => {
		const replacement = {...first, decisionEvaluationInstanceKey: '3', state: 'FAILED' as const};
		worker.use(
			mockQueryDecisionInstancesEndpoint({
				schema: pageSchema(),
				successResponse: HttpResponse.json(
					createQueryDecisionInstancesResponse({items: [first], page: {totalItems: 3, endCursor: 'page-1'}}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
				once: true,
			}),
			mockQueryDecisionInstancesEndpoint({
				schema: pageSchema('page-1'),
				successResponse: HttpResponse.json(
					createQueryDecisionInstancesResponse({items: [second], page: {totalItems: 3, endCursor: 'page-2'}}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
				once: true,
			}),
			mockQueryDecisionInstancesEndpoint({
				schema: pageSchema('page-2'),
				successResponse: HttpResponse.json(
					createQueryDecisionInstancesResponse({items: [replacement], page: {totalItems: 3}}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
				once: true,
			}),
		);

		const screen = await renderQuery(EVALUATION_KEY);

		await expect.element(screen.getByTestId('drd-result')).toHaveTextContent(
			JSON.stringify({
				data: {
					classification: {
						decisionDefinitionId: 'classification',
						decisionEvaluationInstanceKey: '3',
						state: 'FAILED',
					},
					approval: {
						decisionDefinitionId: 'approval',
						decisionEvaluationInstanceKey: '2',
						state: 'EVALUATED',
					},
				},
				isError: false,
				isPending: false,
			}),
		);
	});

	it('should retain the latest repeated evaluation when storage sorts instance ids lexically', async ({worker}) => {
		worker.use(
			mockQueryDecisionInstancesEndpoint({
				schema: pageSchema(),
				successResponse: HttpResponse.json(
					createQueryDecisionInstancesResponse({
						items: [
							{...first, decisionEvaluationInstanceKey: `${EVALUATION_KEY}-10`, state: 'FAILED'},
							{...first, decisionEvaluationInstanceKey: `${EVALUATION_KEY}-2`},
						],
					}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
			}),
		);

		const screen = await renderQuery(EVALUATION_KEY);
		await expect
			.element(screen.getByTestId('drd-result'))
			.toMatchTextContent(`"decisionEvaluationInstanceKey":"${EVALUATION_KEY}-10","state":"FAILED"`);
	});

	it('should keep fetching when the page reports more total items', async ({worker}) => {
		worker.use(
			mockQueryDecisionInstancesEndpoint({
				schema: pageSchema(),
				successResponse: HttpResponse.json(
					createQueryDecisionInstancesResponse({items: [first], page: {hasMoreTotalItems: true, endCursor: 'page-1'}}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
				once: true,
			}),
			mockQueryDecisionInstancesEndpoint({
				schema: pageSchema('page-1'),
				successResponse: HttpResponse.json(createQueryDecisionInstancesResponse({items: [second]})),
				failureResponse: new HttpResponse(null, {status: 400}),
				once: true,
			}),
		);

		const screen = await renderQuery(EVALUATION_KEY);
		await expect.element(screen.getByTestId('drd-result')).toMatchTextContent('"approval"');
	});

	it.for([undefined, ''] as const)('should not fetch without an evaluation key (%s)', async (key) => {
		const screen = await renderQuery(key);
		await expect.element(screen.getByTestId('drd-result')).toHaveTextContent('{"isError":false,"isPending":true}');
	});

	it('should surface a failed page instead of returning a partial family', async ({worker}) => {
		worker.use(
			mockQueryDecisionInstancesEndpoint({
				schema: pageSchema(),
				successResponse: HttpResponse.json(
					createQueryDecisionInstancesResponse({items: [first], page: {totalItems: 2, endCursor: 'page-1'}}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
				once: true,
			}),
			mockQueryDecisionInstancesEndpoint({
				schema: pageSchema('page-1'),
				successResponse: new HttpResponse(null, {status: 500}),
				failureResponse: new HttpResponse(null, {status: 400}),
				once: true,
			}),
		);

		const screen = await renderQuery(EVALUATION_KEY);
		await expect.element(screen.getByTestId('drd-result')).toHaveTextContent('{"isError":true,"isPending":false}');
	});

	it('should reject an incomplete evaluation when a follow-up page is empty', async ({worker}) => {
		worker.use(
			mockQueryDecisionInstancesEndpoint({
				schema: pageSchema(),
				successResponse: HttpResponse.json(
					createQueryDecisionInstancesResponse({items: [first], page: {totalItems: 2, endCursor: 'page-1'}}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
				once: true,
			}),
			mockQueryDecisionInstancesEndpoint({
				schema: pageSchema('page-1'),
				successResponse: HttpResponse.json(createQueryDecisionInstancesResponse({items: [], page: {totalItems: 2}})),
				failureResponse: new HttpResponse(null, {status: 400}),
				once: true,
			}),
		);

		const screen = await renderQuery(EVALUATION_KEY);
		await expect.element(screen.getByTestId('drd-result')).toHaveTextContent('{"isError":true,"isPending":false}');
	});

	it.for([null, 'page-1'] as const)('should fail when a continuation cursor is %s', async (endCursor, {worker}) => {
		worker.use(
			mockQueryDecisionInstancesEndpoint({
				schema: pageSchema(),
				successResponse: HttpResponse.json(
					createQueryDecisionInstancesResponse({items: [first], page: {totalItems: 2, endCursor}}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
				once: true,
			}),
			...(endCursor === null
				? []
				: [
						mockQueryDecisionInstancesEndpoint({
							schema: pageSchema('page-1'),
							successResponse: HttpResponse.json(
								createQueryDecisionInstancesResponse({items: [second], page: {totalItems: 3, endCursor}}),
							),
							failureResponse: new HttpResponse(null, {status: 400}),
							once: true,
						}),
					]),
		);

		const screen = await renderQuery(EVALUATION_KEY);
		await expect.element(screen.getByTestId('drd-result')).toHaveTextContent('{"isError":true,"isPending":false}');
	});

	it('should finish after an empty cursor page when the total count remains capped', async ({worker}) => {
		worker.use(
			mockQueryDecisionInstancesEndpoint({
				schema: pageSchema(),
				successResponse: HttpResponse.json(
					createQueryDecisionInstancesResponse({
						items: [first],
						page: {totalItems: 1, hasMoreTotalItems: true, endCursor: 'page-1'},
					}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
				once: true,
			}),
			mockQueryDecisionInstancesEndpoint({
				schema: pageSchema('page-1'),
				successResponse: HttpResponse.json(
					createQueryDecisionInstancesResponse({items: [], page: {totalItems: 1, hasMoreTotalItems: true}}),
				),
				failureResponse: new HttpResponse(null, {status: 400}),
				once: true,
			}),
		);

		const screen = await renderQuery(EVALUATION_KEY);
		await expect.element(screen.getByTestId('drd-result')).toMatchTextContent('"classification"');
		await expect.element(screen.getByTestId('drd-result')).toMatchTextContent('"isPending":false');
	});
});
