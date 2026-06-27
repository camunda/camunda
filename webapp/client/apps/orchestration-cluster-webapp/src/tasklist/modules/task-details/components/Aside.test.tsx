/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {describe, expect} from 'vitest';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {Aside} from './Aside';

const singleTenantUser = createCurrentUser({
	tenants: [{tenantId: '<default>', name: 'Default', description: null}],
});

const multiTenantUser = createCurrentUser({
	tenants: [
		{tenantId: 'tenant-a', name: 'Tenant A', description: null},
		{tenantId: 'tenant-b', name: 'Tenant B', description: null},
	],
});

const baseProps = {
	creationDate: '2024-01-01T10:00:00.000Z',
	completionDate: null,
	dueDate: null,
	followUpDate: null,
	priority: null,
	candidateUsers: [],
	candidateGroups: [],
	tenantId: '<default>',
	user: singleTenantUser,
};

describe('<Aside />', () => {
	it('should render creation date and "No due date" labels', async () => {
		const screen = await render(<Aside {...baseProps} />);

		await expect.element(screen.getByText('Creation date')).toBeVisible();
		await expect.element(screen.getByText('Due date', {exact: true})).toBeVisible();
		await expect.element(screen.getByText('No due date')).toBeVisible();
	});

	it('should render due date when provided', async () => {
		const screen = await render(<Aside {...baseProps} dueDate="2024-02-15T14:00:00.000Z" />);

		await expect.element(screen.getByText('Due date', {exact: true})).toBeVisible();
		await expect.element(screen.getByText('No due date')).not.toBeInTheDocument();
	});

	it('should render completion date only when present', async () => {
		const screen = await render(<Aside {...baseProps} />);
		await expect.element(screen.getByText('Completion date')).not.toBeInTheDocument();

		await screen.rerender(<Aside {...baseProps} completionDate="2024-01-10T12:00:00.000Z" />);
		await expect.element(screen.getByText('Completion date')).toBeVisible();
	});

	it('should render follow-up date only when present', async () => {
		const screen = await render(<Aside {...baseProps} />);
		await expect.element(screen.getByText('Follow up date')).not.toBeInTheDocument();

		await screen.rerender(<Aside {...baseProps} followUpDate="2024-03-01T09:00:00.000Z" />);
		await expect.element(screen.getByText('Follow up date')).toBeVisible();
	});

	it('should render candidate users and groups as tags', async () => {
		const screen = await render(
			<Aside {...baseProps} candidateUsers={['alice', 'bob']} candidateGroups={['managers']} />,
		);

		await expect.element(screen.getByText('alice')).toBeVisible();
		await expect.element(screen.getByText('bob')).toBeVisible();
		await expect.element(screen.getByText('managers')).toBeVisible();
		await expect.element(screen.getByText('No candidates')).not.toBeInTheDocument();
	});

	it('should render "No candidates" when there are none', async () => {
		const screen = await render(<Aside {...baseProps} candidateUsers={[]} candidateGroups={[]} />);

		await expect.element(screen.getByText('No candidates')).toBeVisible();
	});

	it.for([
		{priority: 20, label: 'Low'},
		{priority: 40, label: 'Medium'},
		{priority: 60, label: 'High'},
		{priority: 80, label: 'Critical'},
	])('should render priority label "$label" for priority $priority', async ({priority, label}) => {
		const screen = await render(<Aside {...baseProps} priority={priority} />);

		await expect.element(screen.getByText('Priority')).toBeVisible();
		await expect.element(screen.getByText(label)).toBeVisible();
	});

	it('should not render priority when null', async () => {
		const screen = await render(<Aside {...baseProps} priority={null} />);

		await expect.element(screen.getByText('Priority')).not.toBeInTheDocument();
	});

	it('should render tenant name when user has multiple tenants', async () => {
		const screen = await render(<Aside {...baseProps} user={multiTenantUser} tenantId="tenant-a" />);

		await expect.element(screen.getByText('Tenant', {exact: true})).toBeVisible();
		await expect.element(screen.getByText('Tenant A')).toBeVisible();
	});

	it('should hide tenant when user has a single tenant', async () => {
		const screen = await render(<Aside {...baseProps} user={singleTenantUser} tenantId="<default>" />);

		await expect.element(screen.getByText('Tenant', {exact: true})).not.toBeInTheDocument();
	});
});
