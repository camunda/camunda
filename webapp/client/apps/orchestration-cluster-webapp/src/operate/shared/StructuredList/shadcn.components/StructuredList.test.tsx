/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, vi} from 'vitest';
import {render} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {StructuredList} from './StructuredList';

const MANY_ROWS = Array.from({length: 30}, (_, index) => ({
	key: `row-${index}`,
	columns: [{cellContent: `Row ${index}`}],
}));

const SizedContainer: React.FC<{children?: React.ReactNode}> = ({children}) => (
	<div style={{height: '200px', display: 'flex', flexDirection: 'column'}}>{children}</div>
);

describe('<StructuredList />', () => {
	it('should render header and row columns', async () => {
		// given / when
		const screen = await render(
			<StructuredList
				label="Process Details"
				headerColumns={[{cellContent: 'Process Definition'}]}
				rows={[{key: 'order-process-v2', columns: [{cellContent: 'Order Process - Version 2'}]}]}
			/>,
		);

		// then
		await expect.element(screen.getByText('Process Definition')).toBeVisible();
		await expect.element(screen.getByText('Order Process - Version 2')).toBeVisible();
	});

	it('should render dynamic rows alongside the static rows', async () => {
		// given / when
		const screen = await render(
			<StructuredList
				label="Process Details"
				headerColumns={[{cellContent: 'Name'}]}
				rows={[{key: 'static-row', columns: [{cellContent: 'Static'}]}]}
				dynamicRows={
					<tr>
						<td>Dynamic content</td>
					</tr>
				}
			/>,
		);

		// then
		await expect.element(screen.getByText('Dynamic content')).toBeVisible();
		await expect.element(screen.getByText('Static')).toBeVisible();
	});

	it('should render multiple rows and columns, preserving row order', async () => {
		// given / when
		const screen = await render(
			<StructuredList
				label="Process Details"
				headerColumns={[{cellContent: 'Name'}, {cellContent: 'Value'}]}
				rows={[
					{key: 'row-1', columns: [{cellContent: 'First'}, {cellContent: '1'}]},
					{key: 'row-2', columns: [{cellContent: 'Second'}, {cellContent: '2'}]},
				]}
			/>,
		);

		// then
		const rows = screen.getByRole('row');
		await expect.element(rows.nth(1)).toHaveTextContent('First1');
		await expect.element(rows.nth(2)).toHaveTextContent('Second2');
	});

	it('should assign the row-level data-testid to its row', async () => {
		// given / when
		const screen = await render(
			<StructuredList
				label="Process Details"
				headerColumns={[{cellContent: 'Name'}]}
				rows={[{key: 'row-1', dataTestId: 'row-1', columns: [{cellContent: 'First'}]}]}
			/>,
		);

		// then
		await expect.element(screen.getByTestId('row-1')).toBeVisible();
	});

	it('should expose the label as an accessible name on the table', async () => {
		// given / when
		const screen = await render(
			<StructuredList label="Process Details" headerColumns={[{cellContent: 'Name'}]} rows={[]} />,
		);

		// then
		await expect.element(screen.getByRole('table', {name: 'Process Details'})).toBeVisible();
	});

	describe('infinite scroll', () => {
		it('should call onVerticalScrollEndReach when scrolling down to the last row', async () => {
			const onVerticalScrollEndReach = vi.fn();
			const screen = await render(
				<StructuredList
					label="Process Details"
					headerColumns={[{cellContent: 'Name'}]}
					rows={MANY_ROWS}
					dataTestId="structured-list"
					onVerticalScrollEndReach={onVerticalScrollEndReach}
				/>,
				{wrapper: SizedContainer},
			);
			await expect.element(screen.getByRole('row', {name: 'Row 29'})).not.toBeInViewport();

			await userEvent.wheel(screen.getByTestId('structured-list'), {delta: {y: 5000}});

			await expect.element(screen.getByRole('row', {name: 'Row 29'})).toBeInViewport();
			await expect.poll(() => onVerticalScrollEndReach).toHaveBeenCalled();
		});

		it('should call onVerticalScrollStartReach when scrolling back up to the first row', async () => {
			const onVerticalScrollStartReach = vi.fn();
			const screen = await render(
				<StructuredList
					label="Process Details"
					headerColumns={[{cellContent: 'Name'}]}
					rows={MANY_ROWS}
					dataTestId="structured-list"
					onVerticalScrollStartReach={onVerticalScrollStartReach}
				/>,
				{wrapper: SizedContainer},
			);
			await userEvent.wheel(screen.getByTestId('structured-list'), {delta: {y: 5000}});
			await expect.element(screen.getByRole('row', {name: 'Row 0'})).not.toBeInViewport();

			await userEvent.wheel(screen.getByTestId('structured-list'), {delta: {y: -5000}});

			await expect.element(screen.getByRole('row', {name: 'Row 0'})).toBeInViewport();
			await expect.poll(() => onVerticalScrollStartReach).toHaveBeenCalled();
		});
	});
});
