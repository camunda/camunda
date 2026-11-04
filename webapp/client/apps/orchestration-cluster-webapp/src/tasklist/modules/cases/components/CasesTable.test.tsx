/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {casesSearchDefaults} from '#/tasklist/modules/cases/searchSchema';
import {CasesTable} from './CasesTable';

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

describe('<CasesTable />', () => {
	it('should list the cases with their business ID and status', async () => {
		const screen = await renderWithRouter(
			() => <CasesTable cases={CASES} totalItems={2} search={casesSearchDefaults} onSearchChange={vi.fn()} />,
			{path: '/tasklist/cases'},
		);

		const homeLoanRow = screen.getByRole('row').filter({hasText: 'HL-28005'});
		const insuranceClaimRow = screen.getByRole('row').filter({hasText: 'IC-28011'});

		await expect.element(homeLoanRow).toMatchTextContent('Home loan application');
		await expect.element(homeLoanRow.getByText('Active')).toBeVisible();
		await expect.element(insuranceClaimRow).toMatchTextContent('Insurance claim');
		await expect.element(insuranceClaimRow.getByText('Incident')).toBeVisible();
	});

	it('should link each row to its case', async () => {
		const screen = await renderWithRouter(
			() => <CasesTable cases={CASES} totalItems={2} search={casesSearchDefaults} onSearchChange={vi.fn()} />,
			{path: '/tasklist/cases'},
		);

		await expect
			.element(screen.getByRole('link', {name: 'Home loan application, HL-28005'}))
			.toHaveAttribute('href', '/tasklist/cases/2251799813685301');
		await expect
			.element(screen.getByRole('link', {name: 'Insurance claim, IC-28011'}))
			.toHaveAttribute('href', '/tasklist/cases/2251799813685302');
	});

	it.for([
		{column: 'Case', sortField: 'processDefinitionName'},
		{column: 'Business ID', sortField: 'businessId'},
	] as const)('should sort by the $column column', async ({column, sortField}) => {
		const onSearchChange = vi.fn();
		const screen = await renderWithRouter(
			() => (
				<CasesTable
					cases={CASES}
					totalItems={2}
					search={{...casesSearchDefaults, page: 2}}
					onSearchChange={onSearchChange}
				/>
			),
			{path: '/tasklist/cases'},
		);

		await userEvent.click(screen.getByRole('button', {name: column}));

		expect(onSearchChange).toHaveBeenCalledWith({sortField, sortOrder: 'asc', page: 1});
	});

	it('should sort by the oldest start date first', async () => {
		const onSearchChange = vi.fn();
		const screen = await renderWithRouter(
			() => <CasesTable cases={CASES} totalItems={2} search={casesSearchDefaults} onSearchChange={onSearchChange} />,
			{path: '/tasklist/cases'},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Started'}));

		expect(onSearchChange).toHaveBeenCalledWith({sortField: 'startDate', sortOrder: 'asc', page: 1});
	});

	it.for([
		{column: 'Started', sortField: 'startDate', sortOrder: 'asc'},
		{column: 'Business ID', sortField: 'businessId', sortOrder: 'desc'},
	] as const)(
		'should clear the sort on the third click of the $column column',
		async ({column, sortField, sortOrder}) => {
			const onSearchChange = vi.fn();
			const screen = await renderWithRouter(
				() => (
					<CasesTable
						cases={CASES}
						totalItems={2}
						search={{...casesSearchDefaults, sortField, sortOrder, page: 2}}
						onSearchChange={onSearchChange}
					/>
				),
				{path: '/tasklist/cases'},
			);

			await userEvent.click(screen.getByRole('button', {name: column}));

			expect(onSearchChange).toHaveBeenCalledWith({sortField: undefined, sortOrder: undefined, page: 1});
		},
	);

	it('should go to the next page', async () => {
		const onSearchChange = vi.fn();
		const screen = await renderWithRouter(
			() => <CasesTable cases={CASES} totalItems={60} search={casesSearchDefaults} onSearchChange={onSearchChange} />,
			{path: '/tasklist/cases'},
		);

		await userEvent.click(screen.getByRole('button', {name: 'Next'}));

		expect(onSearchChange).toHaveBeenCalledWith({pageSize: 25, page: 2});
	});

	it('should return to the first page when the page size changes', async () => {
		const onSearchChange = vi.fn();
		const screen = await renderWithRouter(
			() => (
				<CasesTable
					cases={CASES}
					totalItems={200}
					search={{...casesSearchDefaults, page: 3}}
					onSearchChange={onSearchChange}
				/>
			),
			{path: '/tasklist/cases'},
		);

		await userEvent.click(screen.getByRole('combobox'));
		await userEvent.click(screen.getByRole('option', {name: '50'}));

		expect(onSearchChange).toHaveBeenCalledWith({pageSize: 50, page: 1});
	});

	it('should show an empty state when there are no open cases', async () => {
		const screen = await renderWithRouter(
			() => <CasesTable cases={[]} totalItems={0} search={casesSearchDefaults} onSearchChange={vi.fn()} />,
			{path: '/tasklist/cases'},
		);

		await expect.element(screen.getByRole('heading', {name: 'No open cases'})).toBeVisible();
	});

	it('should show an empty state when no case matches the search', async () => {
		const screen = await renderWithRouter(
			() => (
				<CasesTable
					cases={[]}
					totalItems={0}
					search={{...casesSearchDefaults, search: 'XY-1'}}
					onSearchChange={vi.fn()}
				/>
			),
			{path: '/tasklist/cases'},
		);

		await expect.element(screen.getByRole('heading', {name: 'No matching cases'})).toBeVisible();
		await expect.element(screen.getByText('No open case has a business ID that contains "XY-1".')).toBeVisible();
	});
});
