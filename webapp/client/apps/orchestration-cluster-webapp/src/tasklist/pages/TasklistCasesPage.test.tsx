/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.11';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {casesSearchDefaults, type CasesSearch} from '#/tasklist/modules/cases/searchSchema';
import {TasklistCasesPage} from './TasklistCasesPage';

const DEBOUNCED = {timeout: 3000};

const CASES = [
	createProcessInstance({
		processInstanceKey: '2251799813685301',
		processDefinitionName: 'Home loan application',
		businessId: 'HL-28005',
	}),
	createProcessInstance({
		processInstanceKey: '2251799813685302',
		processDefinitionName: 'Insurance claim',
		businessId: 'IC-28011',
		hasIncident: true,
	}),
];

async function renderPage({
	cases = CASES,
	totalItems = cases.length,
	search = {},
}: {cases?: ProcessInstance[]; totalItems?: number; search?: Partial<CasesSearch>} = {}) {
	const onSearchChange = vi.fn();
	const screen = await renderWithRouter(
		() => (
			<TasklistCasesPage
				cases={cases}
				totalItems={totalItems}
				search={{...casesSearchDefaults, ...search}}
				onSearchChange={onSearchChange}
			/>
		),
		{path: '/tasklist/cases'},
	);

	return {screen, onSearchChange};
}

describe('<TasklistCasesPage />', () => {
	it('should show the open case count in the page header', async () => {
		const {screen} = await renderPage({totalItems: 42});

		await expect.element(screen.getByRole('heading', {name: 'Cases', level: 1})).toBeVisible();
		await expect.element(screen.getByText('42 open cases')).toBeVisible();
	});

	it('should list the cases with their business ID and status', async () => {
		const {screen} = await renderPage();

		const homeLoanRow = screen.getByRole('row').filter({hasText: 'HL-28005'});
		const insuranceClaimRow = screen.getByRole('row').filter({hasText: 'IC-28011'});

		await expect.element(homeLoanRow).toMatchTextContent('Home loan application');
		await expect.element(homeLoanRow.getByText('Active')).toBeVisible();
		await expect.element(insuranceClaimRow).toMatchTextContent('Insurance claim');
		await expect.element(insuranceClaimRow.getByText('Incident')).toBeVisible();
	});

	it('should link each row to its case', async () => {
		const {screen} = await renderPage();

		await expect
			.element(screen.getByRole('link', {name: 'Home loan application, HL-28005'}))
			.toHaveAttribute('href', '/tasklist/cases/2251799813685301');
		await expect
			.element(screen.getByRole('link', {name: 'Insurance claim, IC-28011'}))
			.toHaveAttribute('href', '/tasklist/cases/2251799813685302');
	});

	it('should search by business ID once the reader stops typing', async () => {
		const {screen, onSearchChange} = await renderPage({search: {page: 2}});

		await userEvent.fill(screen.getByRole('searchbox', {name: 'Search by business ID'}), 'HL-28');

		await vi.waitFor(() => expect(onSearchChange).toHaveBeenCalledWith({search: 'HL-28', page: 1}), DEBOUNCED);
	});

	it.for([
		{column: 'Case', sortField: 'processDefinitionName'},
		{column: 'Business ID', sortField: 'businessId'},
	] as const)('should sort by the $column column', async ({column, sortField}) => {
		const {screen, onSearchChange} = await renderPage({search: {page: 2}});

		await userEvent.click(screen.getByRole('button', {name: column}));

		expect(onSearchChange).toHaveBeenCalledWith({sortField, sortOrder: 'asc', page: 1});
	});

	it('should sort by the oldest start date first', async () => {
		const {screen, onSearchChange} = await renderPage();

		await userEvent.click(screen.getByRole('button', {name: 'Started'}));

		expect(onSearchChange).toHaveBeenCalledWith({sortField: 'startDate', sortOrder: 'asc', page: 1});
	});

	it.for([
		{column: 'Started', sortField: 'startDate', sortOrder: 'asc'},
		{column: 'Business ID', sortField: 'businessId', sortOrder: 'desc'},
	] as const)(
		'should clear the sort on the third click of the $column column',
		async ({column, sortField, sortOrder}) => {
			const {screen, onSearchChange} = await renderPage({search: {sortField, sortOrder, page: 2}});

			await userEvent.click(screen.getByRole('button', {name: column}));

			expect(onSearchChange).toHaveBeenCalledWith({sortField: undefined, sortOrder: undefined, page: 1});
		},
	);

	it('should go to the next page', async () => {
		const {screen, onSearchChange} = await renderPage({totalItems: 60});

		await userEvent.click(screen.getByRole('button', {name: 'Next'}));

		expect(onSearchChange).toHaveBeenCalledWith({pageSize: 25, page: 2});
	});

	it('should return to the first page when the page size changes', async () => {
		const {screen, onSearchChange} = await renderPage({totalItems: 200, search: {page: 3}});

		await userEvent.click(screen.getByRole('combobox'));
		await userEvent.click(screen.getByRole('option', {name: '50'}));

		expect(onSearchChange).toHaveBeenCalledWith({pageSize: 50, page: 1});
	});

	it('should show an empty state when there are no open cases', async () => {
		const {screen} = await renderPage({cases: []});

		await expect.element(screen.getByRole('heading', {name: 'No open cases'})).toBeVisible();
		await expect.element(screen.getByText('0 open cases')).toBeVisible();
	});

	it('should show an empty state when no case matches the search', async () => {
		const {screen} = await renderPage({cases: [], search: {search: 'XY-1'}});

		await expect.element(screen.getByRole('heading', {name: 'No matching cases'})).toBeVisible();
		await expect.element(screen.getByText('No open case has a business ID that contains "XY-1".')).toBeVisible();
	});
});
