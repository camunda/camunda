/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, beforeEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockQueryProcessDefinitionsEndpoint} from '#/shared-test-modules/mock-handlers';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {ProcessesHarness} from './ProcessesHarness';

const PROCESS_DEFINITIONS = HttpResponse.json(
	createQueryProcessDefinitionsResponse({
		items: [createProcessDefinition({name: 'Order Process', processDefinitionId: 'order-process', version: 1})],
	}),
);

function renderProcessesPage() {
	return renderWithRouter(ProcessesHarness, {
		path: '/operate/processes',
		initialEntry: '/operate/processes',
	});
}

const ERRORS = {
	ids: 'Key has to be a 16 to 19 digit number, separated by a space or a comma',
	parentInstanceId: 'Key has to be a 16 to 19 digit number',
	batchOperationKey: 'Key has to be a 16 to 19 digit number or a UUID',
} as const;

describe('Validations', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	for (const {filter, label, error, invalidValues} of [
		{
			filter: 'processInstanceKey',
			label: 'Process Instance Key(s)',
			error: ERRORS.ids,
			invalidValues: ['a', '1'],
		},
		{
			filter: 'parentProcessInstanceKey',
			label: 'Parent Process Instance Key',
			error: ERRORS.parentInstanceId,
			invalidValues: ['a', '1', '1111111111111111, 2222222222222222'],
		},
		{
			filter: 'batchOperationKey',
			label: 'Batch Operation Key',
			error: ERRORS.batchOperationKey,
			invalidValues: ['g', 'a'],
		},
	]) {
		it(`should validate ${label}`, async ({worker}) => {
			worker.use(mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}));

			const screen = await renderProcessesPage();
			const getSearch = () => screen.router.state.location.search as Record<string, unknown>;

			await screen.getByRole('button', {name: 'More Filters'}).click();
			await screen.getByTestId(`optional-filter-menuitem-${filter}`).click();

			for (const invalidValue of invalidValues) {
				await userEvent.fill(screen.getByLabelText(label, {exact: true}), invalidValue);

				await expect.element(screen.getByText(error)).toBeVisible();
				expect(getSearch()[filter]).toBeUndefined();

				await userEvent.fill(screen.getByLabelText(label, {exact: true}), '');
				await expect.element(screen.getByText(error)).not.toBeInTheDocument();
			}
		});
	}
});
