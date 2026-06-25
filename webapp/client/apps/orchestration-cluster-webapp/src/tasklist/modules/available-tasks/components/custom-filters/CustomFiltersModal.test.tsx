/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {afterEach, beforeEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {mockCurrentUserEndpoint, mockQueryProcessDefinitionsEndpoint} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {getStateLocally, storeStateLocally} from '#/shared/browser-storage/local-storage';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {CustomFiltersModal} from './CustomFiltersModal';
import {renderWithRouter} from '#/vitest-modules/render-with-router';

const MOCKS = [
	mockCurrentUserEndpoint({
		successResponse: HttpResponse.json(createCurrentUser()),
	}),
	mockQueryProcessDefinitionsEndpoint({
		successResponse: HttpResponse.json(
			createQueryProcessDefinitionsResponse({
				items: [
					createProcessDefinition({
						name: 'Process 0',
						processDefinitionKey: '0',
						version: 1,
					}),
				],
			}),
		),
	}),
];

describe('<CustomFiltersModal />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
		localStorage.clear();
	});

	it('should persist custom filter to localStorage and call onSuccess on apply', async ({worker}) => {
		worker.use(...MOCKS);
		const mockOnSuccess = vi.fn();
		const screen = await renderWithRouter(
			() => <CustomFiltersModal isOpen onClose={() => {}} onSuccess={mockOnSuccess} onDelete={() => {}} />,
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('dialog', {name: /custom filters modal/i})).toBeVisible();

		await userEvent.click(screen.getByText('Unassigned'));
		await userEvent.click(screen.getByRole('button', {name: /^apply$/i}));

		expect(mockOnSuccess).toHaveBeenCalledWith('custom');
		expect(getStateLocally('tasklist.customFilters')).toMatchObject({
			custom: {assignee: 'unassigned'},
		});
	});

	it('should advance to the name step on save and persist a named filter', async ({worker}) => {
		worker.use(...MOCKS);
		const mockOnSuccess = vi.fn();
		const screen = await renderWithRouter(
			() => <CustomFiltersModal isOpen onClose={() => {}} onSuccess={mockOnSuccess} onDelete={() => {}} />,
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('dialog', {name: /custom filters modal/i})).toBeVisible();

		await userEvent.click(screen.getByText('Completed'));
		await userEvent.click(screen.getByRole('button', {name: /^save$/i}));

		await expect.element(screen.getByRole('textbox', {name: /filter name/i})).toBeVisible();

		await userEvent.fill(screen.getByRole('textbox', {name: /filter name/i}), 'My named filter');
		await userEvent.click(screen.getByRole('button', {name: /save and apply/i}));

		expect(mockOnSuccess).toHaveBeenCalledOnce();

		expect(Object.values(getStateLocally('tasklist.customFilters')!)).toContainEqual({
			assignee: 'all',
			status: 'completed',
			name: 'My named filter',
		});
	});

	it('should return to the fields step when the name modal is cancelled', async ({worker}) => {
		worker.use(...MOCKS);
		const screen = await renderWithRouter(
			() => <CustomFiltersModal isOpen onClose={() => {}} onSuccess={() => {}} onDelete={() => {}} />,
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('dialog', {name: /custom filters modal/i})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: /^save$/i}));

		await expect.element(screen.getByRole('textbox', {name: /filter name/i})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: /cancel/i}));

		await expect.element(screen.getByRole('heading', {name: /apply filters/i})).toBeVisible();
	});

	it('should persist an edited filter under its filterId and call onSuccess', async ({worker}) => {
		worker.use(...MOCKS);
		storeStateLocally('tasklist.customFilters', {
			'filter-1': {
				assignee: 'all',
				status: 'completed',
				name: 'My filter',
			},
		});

		const mockOnSuccess = vi.fn();
		const screen = await renderWithRouter(
			() => (
				<CustomFiltersModal
					filterId="filter-1"
					isOpen
					onClose={() => {}}
					onSuccess={mockOnSuccess}
					onDelete={() => {}}
				/>
			),
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('dialog', {name: /custom filters modal/i})).toBeVisible();
		await expect.element(screen.getByRole('radio', {name: /completed/i})).toBeChecked();

		await userEvent.click(screen.getByText('Open'));
		await userEvent.click(screen.getByRole('button', {name: /save and apply/i}));

		expect(mockOnSuccess).toHaveBeenCalledWith('filter-1');
		expect(getStateLocally('tasklist.customFilters')).toMatchObject({
			'filter-1': {status: 'open', name: 'My filter'},
		});
	});

	it('should delete a filter from storage and call onDelete on confirm', async ({worker}) => {
		worker.use(...MOCKS);
		storeStateLocally('tasklist.customFilters', {
			'filter-1': {assignee: 'all', status: 'completed', name: 'My filter'},
		});

		const mockOnDelete = vi.fn();
		const screen = await renderWithRouter(
			() => (
				<CustomFiltersModal
					filterId="filter-1"
					isOpen
					onClose={() => {}}
					onSuccess={() => {}}
					onDelete={mockOnDelete}
				/>
			),
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('dialog', {name: /custom filters modal/i})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: /^delete$/i}));

		await expect.element(screen.getByRole('button', {name: /confirm deletion/i})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: /confirm deletion/i}));

		expect(mockOnDelete).toHaveBeenCalledWith('filter-1');
		const stored = getStateLocally('tasklist.customFilters')!;
		expect(stored).not.toHaveProperty('filter-1');
	});

	it('should load initial values from the stored filter referenced by filterId', async ({worker}) => {
		worker.use(...MOCKS);
		storeStateLocally('tasklist.customFilters', {
			custom: {assignee: 'me', status: 'completed', bpmnProcess: '0'},
		});

		const screen = await renderWithRouter(
			() => <CustomFiltersModal filterId="custom" isOpen onClose={() => {}} onSuccess={() => {}} onDelete={() => {}} />,
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('dialog', {name: /custom filters modal/i})).toBeVisible();
		await expect.element(screen.getByRole('radio', {name: /^me$/i})).toBeChecked();
		await expect.element(screen.getByRole('radio', {name: /completed/i})).toBeChecked();
		await expect.element(screen.getByRole('combobox', {name: /process/i})).toHaveValue('0');
	});
});
