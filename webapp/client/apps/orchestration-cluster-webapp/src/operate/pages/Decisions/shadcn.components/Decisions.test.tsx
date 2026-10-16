/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {Decisions} from './Decisions';

describe('<Decisions />', () => {
	it('should render the sr-only decisions title', async () => {
		const screen = await renderWithRouter(Decisions, {path: '/operate-preview/decisions'});

		await expect.element(screen.getByRole('heading', {name: 'Decisions'})).toBeInTheDocument();
	});

	it('should render the filters panel with the optional filters menu and remaining scaffold placeholders', async () => {
		const screen = await renderWithRouter(Decisions, {path: '/operate-preview/decisions'});

		await expect.element(screen.getByRole('button', {name: 'More Filters'})).toBeVisible();
		await expect.element(screen.getByText('Decision panel placeholder')).toBeVisible();
		await expect.element(screen.getByText('Instances table placeholder')).toBeVisible();
	});

	it('should show optional filters that are active in the URL', async () => {
		const screen = await renderWithRouter(Decisions, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?businessId=eq_order-1',
		});

		await expect.element(screen.getByLabelText('Business ID', {exact: true})).toHaveValue('order-1');
	});

	it('should enable reset when optional filters are active in the URL', async () => {
		const screen = await renderWithRouter(Decisions, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?businessId=eq_order-1',
		});

		await expect.element(screen.getByRole('button', {name: 'Reset filters'})).toBeEnabled();
	});

	it('should enable reset when a decision definition is selected in the URL', async () => {
		const screen = await renderWithRouter(Decisions, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?decisionDefinitionId=invoice',
		});

		await expect.element(screen.getByRole('button', {name: 'Reset filters'})).toBeEnabled();
	});

	it('should write edited optional filters to the URL and keep unrelated search state', async () => {
		const screen = await renderWithRouter(Decisions, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?businessId=eq_order-1&tenantId=tenant-a&failed=false',
		});

		await userEvent.fill(screen.getByLabelText('Business ID', {exact: true}), 'order-2');

		await expect
			.poll(() => screen.router.state.location.search)
			.toEqual({
				businessId: 'eq_order-2',
				tenantId: 'tenant-a',
				failed: false,
			});

		await userEvent.clear(screen.getByLabelText('Business ID', {exact: true}));

		await expect.poll(() => screen.router.state.location.search).toEqual({tenantId: 'tenant-a', failed: false});
	});

	it('should remove active optional filters when resetting', async () => {
		const screen = await renderWithRouter(Decisions, {
			path: '/operate-preview/decisions',
			initialEntry: '/operate-preview/decisions?businessId=eq_order-1',
		});

		await userEvent.click(screen.getByRole('button', {name: 'Reset filters'}));

		await expect.element(screen.getByLabelText('Business ID', {exact: true})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Reset filters'})).toBeDisabled();
	});
});
