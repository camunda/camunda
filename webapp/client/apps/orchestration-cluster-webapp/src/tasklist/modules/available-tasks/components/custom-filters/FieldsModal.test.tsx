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
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import type {NamedCustomFilters} from '#/tasklist/modules/available-tasks/customFiltersSchema';
import {FieldsModal} from './FieldsModal';
import {renderWithRouter} from '#/vitest-modules/render-with-router';

const DEFAULT_VALUES: NamedCustomFilters = {assignee: 'all', status: 'all'};

function getMocks(overrides?: {groups?: string[]}) {
	return [
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser({groups: overrides?.groups ?? []})),
		}),
		mockQueryProcessDefinitionsEndpoint({
			successResponse: HttpResponse.json(
				createQueryProcessDefinitionsResponse({
					items: [
						createProcessDefinition({name: 'Process 0', processDefinitionKey: '0', version: 1}),
						createProcessDefinition({name: 'Process 1', processDefinitionKey: '1', version: 2}),
					],
				}),
			),
		}),
	];
}

describe('<FieldsModal />', () => {
	beforeEach(() => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it('should render the assignee and status radio groups', async ({worker}) => {
		worker.use(...getMocks());
		const screen = await renderWithRouter(
			() => (
				<FieldsModal
					isOpen
					initialValues={DEFAULT_VALUES}
					onClose={() => {}}
					onApply={() => {}}
					onSave={() => {}}
					onEdit={() => {}}
					onDelete={() => {}}
				/>
			),
			{path: '/tasklist'},
		);

		const dialog = screen.getByRole('dialog', {name: /custom filters modal/i});
		await expect.element(dialog).toBeVisible();
		await expect.element(screen.getByRole('heading', {name: /apply filters/i})).toBeVisible();

		const assigneeGroup = screen.getByRole('group', {name: /assignee/i});
		await expect.element(assigneeGroup.getByText('All')).toBeVisible();
		await expect.element(assigneeGroup.getByText('Unassigned')).toBeVisible();
		await expect.element(assigneeGroup.getByText('Me')).toBeVisible();
		await expect.element(assigneeGroup.getByText('User and group')).toBeVisible();

		const statusGroup = screen.getByRole('group', {name: /status/i});
		await expect.element(statusGroup.getByText('All')).toBeVisible();
		await expect.element(statusGroup.getByText('Open')).toBeVisible();
		await expect.element(statusGroup.getByText('Completed')).toBeVisible();
	});

	it('should render the process select with fetched options', async ({worker}) => {
		worker.use(...getMocks());
		const screen = await renderWithRouter(
			() => (
				<FieldsModal
					isOpen
					initialValues={DEFAULT_VALUES}
					onClose={() => {}}
					onApply={() => {}}
					onSave={() => {}}
					onEdit={() => {}}
					onDelete={() => {}}
				/>
			),
			{path: '/tasklist'},
		);

		const combobox = screen.getByRole('combobox', {name: /process/i});
		await expect.element(combobox).toBeVisible();

		await userEvent.selectOptions(combobox, '0');
		await expect.element(combobox).toHaveValue('0');

		await userEvent.selectOptions(combobox, '1');
		await expect.element(combobox).toHaveValue('1');
	});

	it('should reveal user and group inputs when user and group is selected', async ({worker}) => {
		worker.use(...getMocks());
		const screen = await renderWithRouter(
			() => (
				<FieldsModal
					isOpen
					initialValues={DEFAULT_VALUES}
					onClose={() => {}}
					onApply={() => {}}
					onSave={() => {}}
					onEdit={() => {}}
					onDelete={() => {}}
				/>
			),
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('dialog', {name: /custom filters modal/i})).toBeVisible();

		await userEvent.click(screen.getByText('User and group'));

		await expect.element(screen.getByRole('textbox', {name: /assigned to user/i})).toBeVisible();
	});

	it('should render in a group as a select when the current user has groups', async ({worker}) => {
		worker.use(...getMocks({groups: ['accounting', 'sales']}));

		const screen = await renderWithRouter(
			() => (
				<FieldsModal
					isOpen
					initialValues={DEFAULT_VALUES}
					onClose={() => {}}
					onApply={() => {}}
					onSave={() => {}}
					onEdit={() => {}}
					onDelete={() => {}}
				/>
			),
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('dialog', {name: /custom filters modal/i})).toBeVisible();

		await userEvent.click(screen.getByText('User and group'));

		const groupCombobox = screen.getByRole('combobox', {name: /in a group/i});
		await expect.element(groupCombobox).toBeVisible();

		await userEvent.selectOptions(groupCombobox, 'accounting');
		await expect.element(groupCombobox).toHaveValue('accounting');
	});

	it('should reveal advanced fields when the advanced toggle is on', async ({worker}) => {
		worker.use(...getMocks());
		const screen = await renderWithRouter(
			() => (
				<FieldsModal
					isOpen
					initialValues={DEFAULT_VALUES}
					onClose={() => {}}
					onApply={() => {}}
					onSave={() => {}}
					onEdit={() => {}}
					onDelete={() => {}}
				/>
			),
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('dialog', {name: /custom filters modal/i})).toBeVisible();

		await userEvent.click(
			screen
				.getByRole('dialog', {name: 'Custom filters modal'})
				.getByRole('switch', {name: 'Advanced filters', checked: false}),
			{
				force: true,
			},
		);

		await expect.element(screen.getByRole('textbox', {name: /task id/i})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: /add variable/i})).toBeVisible();
	});

	it('should add and remove a variable row', async ({worker}) => {
		worker.use(...getMocks());
		const screen = await renderWithRouter(
			() => (
				<FieldsModal
					isOpen
					initialValues={DEFAULT_VALUES}
					onClose={() => {}}
					onApply={() => {}}
					onSave={() => {}}
					onEdit={() => {}}
					onDelete={() => {}}
				/>
			),
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('dialog', {name: /custom filters modal/i})).toBeVisible();

		await userEvent.click(
			screen
				.getByRole('dialog', {name: 'Custom filters modal'})
				.getByRole('switch', {name: 'Advanced filters', checked: false}),
			{
				force: true,
			},
		);

		await userEvent.click(screen.getByRole('button', {name: /add variable/i}));

		await expect.element(screen.getByRole('textbox', {name: /^name$/i})).toBeVisible();
		await expect.element(screen.getByRole('textbox', {name: /value/i})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: /remove variable/i})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: /remove variable/i}));

		await expect.element(screen.getByRole('button', {name: /add variable/i})).toBeVisible();
	});

	it('should render the tenant select when multi-tenancy is enabled', async ({worker}) => {
		sessionStorage.setItem(
			'clientConfig',
			JSON.stringify(
				createSystemConfiguration({
					deployment: {isMultiTenancyEnabled: true, maxRequestSize: 0},
				}),
			),
		);
		worker.use(...getMocks());
		const screen = await renderWithRouter(
			() => (
				<FieldsModal
					isOpen
					initialValues={DEFAULT_VALUES}
					onClose={() => {}}
					onApply={() => {}}
					onSave={() => {}}
					onEdit={() => {}}
					onDelete={() => {}}
				/>
			),
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('dialog', {name: /custom filters modal/i})).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: /tenant/i})).toBeVisible();
	});

	it('should not render the tenant select when multi-tenancy is disabled', async ({worker}) => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		worker.use(...getMocks());
		const screen = await renderWithRouter(
			() => (
				<FieldsModal
					isOpen
					initialValues={DEFAULT_VALUES}
					onClose={() => {}}
					onApply={() => {}}
					onSave={() => {}}
					onEdit={() => {}}
					onDelete={() => {}}
				/>
			),
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('dialog', {name: /custom filters modal/i})).toBeVisible();
		await expect.element(screen.getByRole('combobox', {name: /process/i})).toBeVisible();
	});

	it('should populate all fields from initialValues', async ({worker}) => {
		worker.use(...getMocks());
		const screen = await renderWithRouter(
			() => (
				<FieldsModal
					isOpen
					initialValues={{
						assignee: 'me',
						status: 'completed',
						bpmnProcess: '0',
						dueDateFrom: new Date('2022-01-01'),
						dueDateTo: new Date('2022-01-01'),
						followUpDateFrom: new Date('2022-01-01'),
						followUpDateTo: new Date('2022-01-01'),
						taskId: 'task-0',
						variables: [{name: 'variable-0', value: '"value-0"'}],
					}}
					onClose={() => {}}
					onApply={() => {}}
					onSave={() => {}}
					onEdit={() => {}}
					onDelete={() => {}}
				/>
			),
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('dialog', {name: /custom filters modal/i})).toBeVisible();
		await expect.element(screen.getByRole('radio', {name: /^me$/i})).toBeChecked();
		await expect.element(screen.getByRole('radio', {name: /completed/i})).toBeChecked();
		await expect.element(screen.getByRole('combobox', {name: /process/i})).toHaveValue('0');
		await expect.element(screen.getByRole('textbox', {name: /task id/i})).toHaveValue('task-0');
		await expect.element(screen.getByRole('textbox', {name: /^name$/i})).toHaveValue('variable-0');
		await expect.element(screen.getByRole('textbox', {name: /value/i})).toHaveValue('"value-0"');
	});

	it('should render the name field when editing a named filter', async ({worker}) => {
		worker.use(...getMocks());
		const screen = await renderWithRouter(
			() => (
				<FieldsModal
					isOpen
					initialValues={{assignee: 'all', status: 'all', name: 'My filter'}}
					onClose={() => {}}
					onApply={() => {}}
					onSave={() => {}}
					onEdit={() => {}}
					onDelete={() => {}}
				/>
			),
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('dialog', {name: /custom filters modal/i})).toBeVisible();
		await expect.element(screen.getByRole('textbox', {name: /filter name/i})).toHaveValue('My filter');
	});

	it('should not render the name field for ad-hoc filters', async ({worker}) => {
		worker.use(...getMocks());
		const screen = await renderWithRouter(
			() => (
				<FieldsModal
					isOpen
					initialValues={DEFAULT_VALUES}
					onClose={() => {}}
					onApply={() => {}}
					onSave={() => {}}
					onEdit={() => {}}
					onDelete={() => {}}
				/>
			),
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('dialog', {name: /custom filters modal/i})).toBeVisible();
		await expect.element(screen.getByRole('heading', {name: /apply filters/i})).toBeVisible();
	});

	it('should call onApply with parsed values when Apply is clicked', async ({worker}) => {
		worker.use(...getMocks());
		const mockOnApply = vi.fn();
		const screen = await renderWithRouter(
			() => (
				<FieldsModal
					isOpen
					initialValues={DEFAULT_VALUES}
					onClose={() => {}}
					onApply={mockOnApply}
					onSave={() => {}}
					onEdit={() => {}}
					onDelete={() => {}}
				/>
			),
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('dialog', {name: /custom filters modal/i})).toBeVisible();

		await userEvent.click(screen.getByText('Unassigned'));
		await userEvent.click(screen.getByRole('button', {name: /^apply$/i}));

		expect(mockOnApply).toHaveBeenCalledOnce();
		expect(mockOnApply.mock.calls[0]![0]).toMatchObject({assignee: 'unassigned'});
	});

	it('should call onSave with values when Save is clicked', async ({worker}) => {
		worker.use(...getMocks());
		const mockOnSave = vi.fn();
		const screen = await renderWithRouter(
			() => (
				<FieldsModal
					isOpen
					initialValues={DEFAULT_VALUES}
					onClose={() => {}}
					onApply={() => {}}
					onSave={mockOnSave}
					onEdit={() => {}}
					onDelete={() => {}}
				/>
			),
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('dialog', {name: /custom filters modal/i})).toBeVisible();

		await userEvent.click(screen.getByText('Completed'));
		await userEvent.click(screen.getByRole('button', {name: /^save$/i}));

		expect(mockOnSave).toHaveBeenCalledOnce();
		expect(mockOnSave.mock.calls[0]![0]).toMatchObject({status: 'completed'});
	});

	it('should call onEdit with values when Save and apply is clicked for a named filter', async ({worker}) => {
		worker.use(...getMocks());
		const mockOnEdit = vi.fn();
		const screen = await renderWithRouter(
			() => (
				<FieldsModal
					isOpen
					initialValues={{assignee: 'all', status: 'all', name: 'My filter'}}
					onClose={() => {}}
					onApply={() => {}}
					onSave={() => {}}
					onEdit={mockOnEdit}
					onDelete={() => {}}
				/>
			),
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('dialog', {name: /custom filters modal/i})).toBeVisible();

		await userEvent.click(screen.getByText('Open'));
		await userEvent.click(screen.getByRole('button', {name: /save and apply/i}));

		expect(mockOnEdit).toHaveBeenCalledOnce();
		expect(mockOnEdit.mock.calls[0]![0]).toMatchObject({status: 'open', name: 'My filter'});
	});

	it('should call onDelete when Delete is clicked for a named filter', async ({worker}) => {
		worker.use(...getMocks());
		const mockOnDelete = vi.fn();
		const screen = await renderWithRouter(
			() => (
				<FieldsModal
					isOpen
					initialValues={{assignee: 'all', status: 'all', name: 'My filter'}}
					onClose={() => {}}
					onApply={() => {}}
					onSave={() => {}}
					onEdit={() => {}}
					onDelete={mockOnDelete}
				/>
			),
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('dialog', {name: /custom filters modal/i})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: /^delete$/i}));

		expect(mockOnDelete).toHaveBeenCalledOnce();
	});

	it('should call onClose when Cancel is clicked', async ({worker}) => {
		worker.use(...getMocks());
		const mockOnClose = vi.fn();
		const screen = await renderWithRouter(
			() => (
				<FieldsModal
					isOpen
					initialValues={DEFAULT_VALUES}
					onClose={mockOnClose}
					onApply={() => {}}
					onSave={() => {}}
					onEdit={() => {}}
					onDelete={() => {}}
				/>
			),
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('dialog', {name: /custom filters modal/i})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: /cancel/i}));

		expect(mockOnClose).toHaveBeenCalledOnce();
	});

	it('should reset fields to defaults when Reset is clicked', async ({worker}) => {
		worker.use(...getMocks());
		const screen = await renderWithRouter(
			() => (
				<FieldsModal
					isOpen
					initialValues={DEFAULT_VALUES}
					onClose={() => {}}
					onApply={() => {}}
					onSave={() => {}}
					onEdit={() => {}}
					onDelete={() => {}}
				/>
			),
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('dialog', {name: /custom filters modal/i})).toBeVisible();

		await userEvent.click(screen.getByText('Completed'));
		await expect.element(screen.getByRole('radio', {name: /completed/i})).toBeChecked();

		await userEvent.click(screen.getByRole('button', {name: /reset/i}));

		await expect.element(screen.getByRole('radio', {name: /completed/i})).not.toBeChecked();
	});
});
