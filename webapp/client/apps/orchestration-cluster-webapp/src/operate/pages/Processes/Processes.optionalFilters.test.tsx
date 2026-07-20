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

function renderProcessesPage(searchParams?: Record<string, string>) {
	const query = searchParams ? `?${new URLSearchParams(searchParams).toString()}` : '';
	return renderWithRouter(ProcessesHarness, {
		path: '/operate/processes',
		initialEntry: `/operate/processes${query}`,
	});
}

const OPTIONAL_FILTER_LABELS = [
	'Process Instance Key(s)',
	'Business ID',
	'Batch Operation Key',
	'Parent Process Instance Key',
	'Error Message',
	'Failed job but retries left',
	'Start Date Range',
	'End Date Range',
] as const;

describe('Optional Filters', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should initially hide optional filters', async ({worker}) => {
		worker.use(mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}));

		const screen = await renderProcessesPage();

		await expect.element(screen.getByRole('button', {name: 'More Filters'})).toBeVisible();
		for (const label of OPTIONAL_FILTER_LABELS) {
			await expect.element(screen.getByLabelText(label, {exact: true})).not.toBeInTheDocument();
		}
	});

	for (const {filter, label, remainingFilter} of [
		{filter: 'processInstanceKey', label: 'Process Instance Key(s)', remainingFilter: 'businessId'},
		{filter: 'businessId', label: 'Business ID', remainingFilter: 'processInstanceKey'},
		{filter: 'batchOperationKey', label: 'Batch Operation Key', remainingFilter: 'processInstanceKey'},
		{filter: 'parentProcessInstanceKey', label: 'Parent Process Instance Key', remainingFilter: 'processInstanceKey'},
		{filter: 'errorMessage', label: 'Error Message', remainingFilter: 'processInstanceKey'},
		{filter: 'hasRetriesLeft', label: 'Failed job but retries left', remainingFilter: 'processInstanceKey'},
	]) {
		it(`should display ${label} field on click`, async ({worker}) => {
			worker.use(mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}));

			const screen = await renderProcessesPage();

			await userEvent.click(screen.getByRole('button', {name: 'More Filters'}));
			await userEvent.click(screen.getByTestId(`optional-filter-menuitem-${filter}`));

			await expect.element(screen.getByLabelText(label, {exact: true})).toBeVisible();

			// Reopening must show the menu again with a remaining item, not merely lack the selected one —
			// a menu that fails to reopen at all also satisfies a bare "selected item absent" assertion.
			await userEvent.click(screen.getByRole('button', {name: 'More Filters'}));
			await expect.element(screen.getByTestId(`optional-filter-menuitem-${filter}`)).not.toBeInTheDocument();
			await expect.element(screen.getByTestId(`optional-filter-menuitem-${remainingFilter}`)).toBeVisible();
		});
	}

	for (const {filter, label} of [
		{filter: 'startDateRange', label: 'Start Date Range'},
		{filter: 'endDateRange', label: 'End Date Range'},
	]) {
		it(`should display ${label} field on click`, async ({worker}) => {
			worker.use(mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}));

			const screen = await renderProcessesPage();

			await screen.getByRole('button', {name: 'More Filters'}).click();
			await screen.getByTestId(`optional-filter-menuitem-${filter}`).click();

			await expect.element(screen.getByLabelText(label, {exact: true})).toBeVisible();
			await expect.element(screen.getByTestId('date-range-modal')).toHaveClass(/is-visible/);

			await screen.getByRole('button', {name: 'Cancel'}).click();

			await screen.getByRole('button', {name: 'More Filters'}).click();
			await expect.element(screen.getByTestId(`optional-filter-menuitem-${filter}`)).not.toBeInTheDocument();
		});
	}

	it('should hide more filters button when all optional filters are visible', async ({worker}) => {
		worker.use(mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}));

		const screen = await renderProcessesPage({
			processInstanceKey: '2251799813685467',
			parentProcessInstanceKey: '1954699813693756',
			errorMessage: 'a random error',
			startDateFrom: '2021-02-21T20:00:00',
			startDateTo: '2021-02-21T18:17:18',
			endDateFrom: '2021-02-23T22:00:00',
			endDateTo: '2021-02-23T18:17:18',
			batchOperationKey: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
			businessId: 'eq_order-12345',
			hasRetriesLeft: 'true',
		});

		await expect.element(screen.getByRole('button', {name: 'More Filters'})).not.toBeInTheDocument();

		await userEvent.hover(screen.getByLabelText('Error Message', {exact: true}));
		await screen.getByRole('button', {name: 'Remove Error Message Filter'}).click();

		await expect.element(screen.getByRole('button', {name: 'More Filters'})).toBeVisible();
	});

	it('should delete optional filters', async ({worker}) => {
		worker.use(mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}));

		const screen = await renderProcessesPage({
			process: 'order-process',
			version: '1',
			processInstanceKey: '2251799813685467',
			parentProcessInstanceKey: '1954699813693756',
			errorMessage: 'a random error',
			startDateFrom: '2021-02-21T20:00:00',
			startDateTo: '2021-02-21T18:17:18',
			endDateFrom: '2021-02-23T22:00:00',
			endDateTo: '2021-02-23T18:17:18',
			batchOperationKey: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
			businessId: 'eq_order-12345',
			hasRetriesLeft: 'true',
		});
		const getSearch = () => screen.router.state.location.search as Record<string, unknown>;

		await expect.element(screen.getByLabelText('Process Instance Key(s)', {exact: true})).toBeVisible();
		await expect.element(screen.getByLabelText('Parent Process Instance Key', {exact: true})).toBeVisible();
		await expect.element(screen.getByLabelText('Error Message', {exact: true})).toBeVisible();
		await expect.element(screen.getByLabelText('Start Date Range', {exact: true})).toBeVisible();
		await expect.element(screen.getByLabelText('End Date Range', {exact: true})).toBeVisible();
		await expect.element(screen.getByLabelText('Batch Operation Key', {exact: true})).toBeVisible();
		await expect.element(screen.getByLabelText('Business ID', {exact: true})).toBeVisible();
		await expect.element(screen.getByRole('checkbox', {name: 'Failed job but retries left'})).toBeVisible();

		const removals: Array<{label: string; removedKeys: string[]; isCheckbox?: boolean}> = [
			{label: 'Process Instance Key(s)', removedKeys: ['processInstanceKey']},
			{label: 'Parent Process Instance Key', removedKeys: ['parentProcessInstanceKey']},
			{label: 'Error Message', removedKeys: ['errorMessage']},
			{label: 'Start Date Range', removedKeys: ['startDateFrom', 'startDateTo']},
			{label: 'End Date Range', removedKeys: ['endDateFrom', 'endDateTo']},
			{label: 'Batch Operation Key', removedKeys: ['batchOperationKey']},
			{label: 'Business ID', removedKeys: ['businessId']},
			{label: 'Failed job but retries left', removedKeys: ['hasRetriesLeft'], isCheckbox: true},
		];

		for (const {label, removedKeys, isCheckbox} of removals) {
			// The checkbox's native input is visually covered by its own label, which fails
			// real-browser hover actionability — hover the visible label text instead.
			await userEvent.hover(
				isCheckbox ? screen.getByText(label, {exact: true}) : screen.getByLabelText(label, {exact: true}),
			);
			await screen.getByRole('button', {name: `Remove ${label} Filter`}).click();

			for (const key of removedKeys) {
				await expect.poll(() => getSearch()[key]).toBeUndefined();
			}
			await expect.element(screen.getByLabelText(label, {exact: true})).not.toBeInTheDocument();
		}

		await expect
			.poll(() => getSearch())
			.toEqual({
				process: 'order-process',
				version: 1,
			});
	});

	it('should remove optional filters on filter reset', async ({worker}) => {
		worker.use(mockQueryProcessDefinitionsEndpoint({successResponse: PROCESS_DEFINITIONS}));

		const screen = await renderProcessesPage({
			process: 'order-process',
			version: '1',
			processInstanceKey: '2251799813685467',
			parentProcessInstanceKey: '1954699813693756',
			errorMessage: 'a random error',
			startDateFrom: '2021-02-21T20:00:00',
			startDateTo: '2021-02-21T18:17:18',
			endDateFrom: '2021-02-23T22:00:00',
			endDateTo: '2021-02-23T18:17:18',
			batchOperationKey: '2f5b1beb-cbeb-41c8-a2f0-4c0bcf76c4ee',
			businessId: 'eq_order-12345',
		});
		const getSearch = () => screen.router.state.location.search as Record<string, unknown>;

		await expect.element(screen.getByLabelText('Process Instance Key(s)', {exact: true})).toBeVisible();
		await expect.element(screen.getByLabelText('Business ID', {exact: true})).toHaveValue('order-12345');

		await screen.getByRole('button', {name: 'Reset Filters'}).click();

		await expect.poll(() => getSearch()).toEqual({});

		await expect.element(screen.getByLabelText('Process Instance Key(s)', {exact: true})).not.toBeInTheDocument();
		await expect.element(screen.getByLabelText('Parent Process Instance Key', {exact: true})).not.toBeInTheDocument();
		await expect.element(screen.getByLabelText('Error Message', {exact: true})).not.toBeInTheDocument();
		await expect.element(screen.getByLabelText('Start Date Range', {exact: true})).not.toBeInTheDocument();
		await expect.element(screen.getByLabelText('End Date Range', {exact: true})).not.toBeInTheDocument();
		await expect.element(screen.getByLabelText('Batch Operation Key', {exact: true})).not.toBeInTheDocument();
		await expect.element(screen.getByLabelText('Business ID', {exact: true})).not.toBeInTheDocument();
	});
});
