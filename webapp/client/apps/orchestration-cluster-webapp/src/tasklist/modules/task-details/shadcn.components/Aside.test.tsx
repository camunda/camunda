/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
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
	businessId: null,
	user: singleTenantUser,
} satisfies React.ComponentProps<typeof Aside>;

describe('<Aside />', () => {
	it('should render creation date and "No due date" labels', async () => {
		const screen = await render(<Aside {...baseProps} />);
		const aside = screen.getByRole('complementary', {name: 'Task details right panel'});

		await expect.element(aside.getByText('Details', {exact: true})).toBeVisible();
		await expect.element(aside.getByText('Creation date')).toBeVisible();
		await expect.element(aside.getByText('Due date', {exact: true})).toBeVisible();
		await expect.element(aside.getByText('No due date')).toBeVisible();
	});

	it('should render due date when provided', async () => {
		const screen = await render(<Aside {...baseProps} dueDate="2024-02-15T14:00:00.000Z" />);
		const aside = screen.getByRole('complementary', {name: 'Task details right panel'});

		await expect.element(aside.getByText('Due date', {exact: true})).toBeVisible();
		await expect.element(aside.getByText('No due date')).not.toBeInTheDocument();
	});

	it('should render completion date only when present', async () => {
		const screen = await render(<Aside {...baseProps} />);
		const aside = screen.getByRole('complementary', {name: 'Task details right panel'});
		await expect.element(aside.getByText('Completion date')).not.toBeInTheDocument();

		await screen.rerender(<Aside {...baseProps} completionDate="2024-01-10T12:00:00.000Z" />);
		await expect.element(aside.getByText('Completion date')).toBeVisible();
	});

	it('should render follow-up date only when present', async () => {
		const screen = await render(<Aside {...baseProps} />);
		const aside = screen.getByRole('complementary', {name: 'Task details right panel'});
		await expect.element(aside.getByText('Follow up date')).not.toBeInTheDocument();

		await screen.rerender(<Aside {...baseProps} followUpDate="2024-03-01T09:00:00.000Z" />);
		await expect.element(aside.getByText('Follow up date')).toBeVisible();
	});

	it('should render candidate users and groups as badges', async () => {
		const screen = await render(
			<Aside {...baseProps} candidateUsers={['alice', 'bob']} candidateGroups={['managers']} />,
		);
		const aside = screen.getByRole('complementary', {name: 'Task details right panel'});

		await expect.element(aside.getByText('alice')).toBeVisible();
		await expect.element(aside.getByText('bob')).toBeVisible();
		await expect.element(aside.getByText('managers')).toBeVisible();
		await expect.element(aside.getByText('No candidates')).not.toBeInTheDocument();
	});

	it('should render "No candidates" when there are none', async () => {
		const screen = await render(<Aside {...baseProps} candidateUsers={[]} candidateGroups={[]} />);
		const aside = screen.getByRole('complementary', {name: 'Task details right panel'});

		await expect.element(aside.getByText('No candidates')).toBeVisible();
	});

	it.for([
		{priority: 20, label: 'Low'},
		{priority: 40, label: 'Medium'},
		{priority: 60, label: 'High'},
		{priority: 80, label: 'Critical'},
	])('should render priority label "$label" for priority $priority', async ({priority, label}) => {
		const screen = await render(<Aside {...baseProps} priority={priority} />);
		const aside = screen.getByRole('complementary', {name: 'Task details right panel'});

		await expect.element(aside.getByText('Priority')).toBeVisible();
		await expect.element(aside.getByText(label)).toBeVisible();
	});

	it('should not render priority when null', async () => {
		const screen = await render(<Aside {...baseProps} priority={null} />);
		const aside = screen.getByRole('complementary', {name: 'Task details right panel'});

		await expect.element(aside.getByText('Priority')).not.toBeInTheDocument();
	});

	it('should render business id when present', async () => {
		const screen = await render(<Aside {...baseProps} businessId="order-123" />);
		const aside = screen.getByRole('complementary', {name: 'Task details right panel'});

		await expect.element(aside.getByText('Business ID')).toBeVisible();
		await expect.element(aside.getByText('order-123')).toBeVisible();
	});

	it('should not render business id when null', async () => {
		const screen = await render(<Aside {...baseProps} businessId={null} />);
		const aside = screen.getByRole('complementary', {name: 'Task details right panel'});

		await expect.element(aside.getByText('Business ID')).not.toBeInTheDocument();
	});

	it('should render tenant name when user has multiple tenants', async () => {
		const screen = await render(<Aside {...baseProps} user={multiTenantUser} tenantId="tenant-a" />);
		const aside = screen.getByRole('complementary', {name: 'Task details right panel'});

		await expect.element(aside.getByText('Tenant', {exact: true})).toBeVisible();
		await expect.element(aside.getByText('Tenant A')).toBeVisible();
	});

	it('should hide tenant when user has a single tenant', async () => {
		const screen = await render(<Aside {...baseProps} user={singleTenantUser} tenantId="<default>" />);
		const aside = screen.getByRole('complementary', {name: 'Task details right panel'});

		await expect.element(aside.getByText('Tenant', {exact: true})).not.toBeInTheDocument();
	});
});
