/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {afterEach, describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {storeStateLocally, clearStateLocally} from '#/shared/browser-storage/local-storage';
import {DeleteFilterModal} from './DeleteFilterModal';

describe('<DeleteFilterModal />', () => {
	afterEach(() => {
		clearStateLocally('tasklist.customFilters');
	});

	it('should render the filter name from local storage', async () => {
		storeStateLocally('tasklist.customFilters', {
			'filter-1': {assignee: 'all', status: 'all', name: 'My saved filter'},
		});

		const screen = await render(
			<DeleteFilterModal isOpen filterId="filter-1" onClose={() => {}} onDelete={() => {}} />,
		);

		await expect.element(screen.getByText(/my saved filter/i)).toBeVisible();
	});

	it('should call onDelete with the filterId on confirm', async () => {
		const mockOnDelete = vi.fn();
		storeStateLocally('tasklist.customFilters', {
			'filter-1': {assignee: 'all', status: 'all', name: 'My saved filter'},
		});

		const screen = await render(
			<DeleteFilterModal isOpen filterId="filter-1" onClose={() => {}} onDelete={mockOnDelete} />,
		);

		await userEvent.click(screen.getByRole('button', {name: /confirm deletion/i}));

		expect(mockOnDelete).toHaveBeenCalledOnce();
		expect(mockOnDelete).toHaveBeenCalledWith('filter-1');
	});

	it('should call onClose on cancel', async () => {
		const mockOnClose = vi.fn();
		storeStateLocally('tasklist.customFilters', {
			'filter-1': {assignee: 'all', status: 'all', name: 'My saved filter'},
		});

		const screen = await render(
			<DeleteFilterModal isOpen filterId="filter-1" onClose={mockOnClose} onDelete={() => {}} />,
		);

		await userEvent.click(screen.getByRole('button', {name: /cancel/i}));

		expect(mockOnClose).toHaveBeenCalledOnce();
	});
});
