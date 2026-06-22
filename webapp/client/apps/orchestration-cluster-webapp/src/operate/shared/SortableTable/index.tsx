/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {DataTableSkeleton, Table, TableBody, TableCell, TableHead, TableRow} from '@carbon/react';
import {TableContainer, LoadingOverlay, EmptyStateContainer} from './styled';
import {ColumnHeader} from './ColumnHeader';

type Column<TRow> = {
	key: string;
	label: string;
	sortKey?: string;
	isDefault?: boolean;
	defaultOrder?: 'asc' | 'desc';
	render: (row: TRow) => React.ReactNode;
};

type Props<TRow> = {
	columns: Column<TRow>[];
	rows: TRow[];
	rowKey: (row: TRow) => string;
	isLoading: boolean;
	isFetching: boolean;
	emptyState?: React.ReactNode;
	'data-testid'?: string;
};

function SortableTable<TRow>({
	columns,
	rows,
	rowKey,
	isLoading,
	isFetching,
	emptyState,
	'data-testid': dataTestId,
}: Props<TRow>) {
	if (isLoading) {
		return <DataTableSkeleton columnCount={columns.length} rowCount={5} showHeader={false} showToolbar={false} />;
	}

	return (
		<TableContainer data-testid={dataTestId}>
			{isFetching && <LoadingOverlay aria-hidden />}
			<Table>
				<TableHead>
					<TableRow>
						{columns.map((col) =>
							col.sortKey !== undefined ? (
								<ColumnHeader
									key={col.key}
									sortKey={col.sortKey}
									label={col.label}
									isDefault={col.isDefault}
									defaultOrder={col.defaultOrder}
								/>
							) : (
								<th key={col.key} scope="col">
									{col.label}
								</th>
							),
						)}
					</TableRow>
				</TableHead>
				<TableBody>
					{rows.length === 0 && emptyState !== undefined ? (
						<tr>
							<td colSpan={columns.length}>
								<EmptyStateContainer>{emptyState}</EmptyStateContainer>
							</td>
						</tr>
					) : (
						rows.map((row) => (
							<TableRow key={rowKey(row)}>
								{columns.map((col) => (
									<TableCell key={col.key}>{col.render(row)}</TableCell>
								))}
							</TableRow>
						))
					)}
				</TableBody>
			</Table>
		</TableContainer>
	);
}

export {SortableTable};
