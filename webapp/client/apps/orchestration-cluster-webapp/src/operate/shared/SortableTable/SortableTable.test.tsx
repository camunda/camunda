/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {SortableTable} from './index';

type Row = {id: string; name: string; error: string};

it('should keep failure expansion attached to row identity when paginating', async () => {
	const rows: Row[] = [
		{id: '1', name: 'First process', error: 'First failure'},
		{id: '2', name: 'Second process', error: 'Second failure'},
	];
	function Pages() {
		const [page, setPage] = useState(0);
		return (
			<>
				<button type="button" onClick={() => setPage((current) => 1 - current)}>
					Next page
				</button>
				<SortableTable
					columns={[{key: 'name', label: 'Name', render: (row: Row) => row.name}]}
					rows={[rows[page]!]}
					rowKey={(row) => row.id}
					rowOperationError={(row) => ({message: row.error, expandLabel: `Show failure for ${row.id}`})}
					failureDetailsLabel="Failure details"
				/>
			</>
		);
	}
	const screen = await render(<Pages />);

	await userEvent.click(screen.getByRole('button', {name: 'Show failure for 1'}));
	await expect.element(screen.getByText('First failure')).toBeVisible();
	await userEvent.click(screen.getByRole('button', {name: 'Next page'}));
	await expect.element(screen.getByText('First failure')).not.toBeInTheDocument();
	await expect.element(screen.getByText('Second failure')).not.toBeInTheDocument();
	await userEvent.click(screen.getByRole('button', {name: 'Show failure for 2'}));
	await expect.element(screen.getByText('Second failure')).toBeVisible();
	await userEvent.click(screen.getByRole('button', {name: 'Next page'}));
	await expect.element(screen.getByText('Second failure')).not.toBeInTheDocument();
	await expect.element(screen.getByText('First failure')).toBeVisible();
});

it('should keep independent failure rows expanded', async () => {
	const rows: Row[] = [
		{id: '1', name: 'First process', error: 'First failure'},
		{id: '2', name: 'Second process', error: 'Second failure'},
	];
	const screen = await render(
		<SortableTable
			columns={[{key: 'name', label: 'Name', render: (row: Row) => row.name}]}
			rows={rows}
			rowKey={(row) => row.id}
			rowOperationError={(row) => ({message: row.error, expandLabel: `Show failure for ${row.id}`})}
			failureDetailsLabel="Failure details"
		/>,
	);

	await userEvent.click(screen.getByRole('button', {name: 'Show failure for 1'}));
	await userEvent.click(screen.getByRole('button', {name: 'Show failure for 2'}));

	await expect.element(screen.getByText('First failure')).toBeVisible();
	await expect.element(screen.getByText('Second failure')).toBeVisible();
});
