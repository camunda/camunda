/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {HttpResponse} from 'msw';
import {z} from 'zod';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {createProcessDefinition} from '#/shared-test-modules/api-mocks/process-definitions';
import {createQueryProcessInstancesResponse} from '#/shared-test-modules/api-mocks/process-instances';
import {mockQueryProcessInstancesEndpoint} from '#/shared-test-modules/mock-handlers';
import {MigrationConfirmationModal} from './MigrationConfirmationModal';

const SOURCE = createProcessDefinition({processDefinitionKey: 'source-key', name: 'Invoice process', version: 1});
const TARGET = createProcessDefinition({processDefinitionKey: 'target-key', name: 'Invoice process', version: 2});
const SCOPE = {
	filter: {processDefinitionKey: {$eq: 'source-key'}},
	statisticsFilter: {},
	selectedCount: 2,
};
const COUNT_REQUEST_SCHEMA = z.strictObject({
	filter: z.strictObject({processDefinitionKey: z.strictObject({$eq: z.literal('source-key')})}),
	page: z.strictObject({limit: z.literal(0)}),
});

function renderModal(hasElementMapping: boolean) {
	const queryClient = new QueryClient({defaultOptions: {queries: {retry: false}}});
	return render(
		<MigrationConfirmationModal
			source={SOURCE}
			target={TARGET}
			scope={SCOPE}
			hasElementMapping={hasElementMapping}
			onClose={() => {}}
			onSubmit={() => {}}
		/>,
		{
			wrapper: ({children}) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>,
		},
	);
}

describe('<MigrationConfirmationModal />', () => {
	it.for([
		{mapping: 'with mapped elements', hasElementMapping: true, expected: 'enabled'},
		{mapping: 'without mapped elements', hasElementMapping: false, expected: 'disabled'},
	] as const)(
		'should keep Confirm $expected after typing MIGRATE $mapping',
		async ({hasElementMapping, expected}, {worker}) => {
			worker.use(
				mockQueryProcessInstancesEndpoint({
					schema: COUNT_REQUEST_SCHEMA,
					successResponse: HttpResponse.json(
						createQueryProcessInstancesResponse({items: [], page: {totalItems: 2, hasMoreTotalItems: false}}),
					),
					failureResponse: new HttpResponse(null, {status: 400}),
				}),
			);
			const screen = await renderModal(hasElementMapping);
			await expect.element(screen.getByText(/^You are about to migrate 2 process instances /)).toBeVisible();

			await userEvent.fill(screen.getByRole('textbox'), 'MIGRATE');

			const confirm = screen.getByRole('button', {name: 'Confirm'});
			if (expected === 'enabled') {
				await expect.element(confirm).toBeEnabled();
			} else {
				await expect.element(confirm).toBeDisabled();
			}
		},
	);

	it('should keep Confirm disabled while the instances are being counted', async ({worker}) => {
		worker.use(
			mockQueryProcessInstancesEndpoint({
				delay: 'infinite',
				successResponse: HttpResponse.json(createQueryProcessInstancesResponse()),
			}),
		);
		const screen = await renderModal(true);

		await userEvent.fill(screen.getByRole('textbox'), 'MIGRATE');

		await expect.element(screen.getByText(/^You are about to migrate the selected process instances /)).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Confirm'})).toBeDisabled();
	});
});
