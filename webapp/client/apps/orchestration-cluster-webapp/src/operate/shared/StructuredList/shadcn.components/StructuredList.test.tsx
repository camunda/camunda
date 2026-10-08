/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, vi} from 'vitest';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {setUpFakeIntersectionObserver} from '#/vitest-modules/fake-intersection-observer';
import {StructuredList} from './StructuredList';

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

	it('should flush-align the leading cell of both dynamic and static rows when isFlush is set', async () => {
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
				isFlush
			/>,
		);

		// then: both the caller-rendered dynamic row and the auto-rendered static row are flush
		const dynamicCell = screen.getByText('Dynamic content').element().closest('td')!;
		const staticCell = screen.getByText('Static').element().closest('td')!;
		expect(getComputedStyle(dynamicCell).paddingLeft).toBe('0px');
		expect(getComputedStyle(staticCell).paddingLeft).toBe('0px');
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
		const {getObserver} = setUpFakeIntersectionObserver();

		it('should call onVerticalScrollStartReach with a scrollDown callback when scrolling up past the first row', async () => {
			// given
			const onVerticalScrollStartReach = vi.fn();
			const screen = await render(
				<StructuredList
					label="Process Details"
					headerColumns={[{cellContent: 'Name'}]}
					rows={[
						{key: 'row-1', columns: [{cellContent: 'First'}]},
						{key: 'row-2', columns: [{cellContent: 'Second'}]},
					]}
					dataTestId="structured-list"
					onVerticalScrollStartReach={onVerticalScrollStartReach}
				/>,
			);
			const container = screen.getByTestId('structured-list').element() as HTMLElement;
			const scrollToSpy = vi.spyOn(container, 'scrollTo').mockImplementation(() => {});
			const firstRow = container.querySelector('tbody tr:first-child');
			if (firstRow === null) {
				throw new Error('Expected at least one row to be rendered');
			}

			// when
			const observer = getObserver();
			Object.defineProperty(container, 'scrollTop', {value: 100, configurable: true});
			observer.intersect(firstRow);
			Object.defineProperty(container, 'scrollTop', {value: 20, configurable: true});
			observer.intersect(firstRow);

			// then
			expect(onVerticalScrollStartReach).toHaveBeenCalledTimes(1);
			const scrollDown = onVerticalScrollStartReach.mock.calls[0]?.[0] as (distance: number) => void;
			scrollDown(10);
			expect(scrollToSpy).toHaveBeenCalledWith(0, 30);
		});

		it('should call onVerticalScrollEndReach with a scrollUp callback when scrolling down past the last row', async () => {
			// given
			const onVerticalScrollEndReach = vi.fn();
			const screen = await render(
				<StructuredList
					label="Process Details"
					headerColumns={[{cellContent: 'Name'}]}
					rows={[
						{key: 'row-1', columns: [{cellContent: 'First'}]},
						{key: 'row-2', columns: [{cellContent: 'Second'}]},
					]}
					dataTestId="structured-list"
					onVerticalScrollEndReach={onVerticalScrollEndReach}
				/>,
			);
			const container = screen.getByTestId('structured-list').element() as HTMLElement;
			const scrollToSpy = vi.spyOn(container, 'scrollTo').mockImplementation(() => {});
			const lastRow = container.querySelector('tbody tr:last-child');
			if (lastRow === null) {
				throw new Error('Expected at least one row to be rendered');
			}

			// when
			const observer = getObserver();
			Object.defineProperty(container, 'scrollTop', {value: 0, configurable: true});
			observer.intersect(lastRow);
			Object.defineProperty(container, 'scrollTop', {value: 100, configurable: true});
			observer.intersect(lastRow);

			// then
			expect(onVerticalScrollEndReach).toHaveBeenCalledTimes(1);
			const scrollUp = onVerticalScrollEndReach.mock.calls[0]?.[0] as (distance: number) => void;
			scrollUp(10);
			expect(scrollToSpy).toHaveBeenCalledWith(0, 90);
		});
	});

	it('should remove the left padding on the leading cell when flush (default)', async () => {
		// given / when
		const screen = await render(
			<StructuredList
				label="Process Details"
				headerColumns={[{cellContent: 'Name'}]}
				rows={[{key: 'row-1', columns: [{cellContent: 'First'}]}]}
			/>,
		);

		// then
		const cell = screen.getByText('First').element().closest('td')!;
		expect(getComputedStyle(cell).paddingLeft).toBe('0px');
	});

	it('should keep the default cell padding when not flush', async () => {
		// given / when
		const screen = await render(
			<StructuredList
				label="Process Details"
				headerColumns={[{cellContent: 'Name'}]}
				rows={[{key: 'row-1', columns: [{cellContent: 'First'}]}]}
				isFlush={false}
			/>,
		);

		// then
		const cell = screen.getByText('First').element().closest('td')!;
		expect(getComputedStyle(cell).paddingLeft).not.toBe('0px');
	});
});
