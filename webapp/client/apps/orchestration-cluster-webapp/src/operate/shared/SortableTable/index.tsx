/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef} from 'react';
import {
	Table,
	TableBody,
	TableCell,
	TableHead,
	TableHeader,
	TableRow,
	TableSelectAll,
	TableSelectRow,
} from '@carbon/react';
import {TableContainer, ScrollContainer, LoadingOverlay, EmptyStateContainer} from './styled';
import {ColumnHeader} from './ColumnHeader';
import {InfiniteScroller} from '../InfiniteScroller/InfiniteScroller';

type TableSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl';

type Column<TRow> = {
	key: string;
	label: string;
	sortKey?: string;
	isDefault?: boolean;
	defaultOrder?: 'asc' | 'desc';
	render: (row: TRow) => React.ReactNode;
};

type BaseProps<TRow> = {
	columns: Column<TRow>[];
	rows: TRow[];
	rowKey: (row: TRow) => string;
	size?: TableSize;
	isFetching?: boolean;
	emptyState?: React.ReactNode;
	onSort?: (sortKey: string, order: 'asc' | 'desc') => void;
	onVerticalScrollStartReach?: React.ComponentProps<typeof InfiniteScroller>['onVerticalScrollStartReach'];
	onVerticalScrollEndReach?: React.ComponentProps<typeof InfiniteScroller>['onVerticalScrollEndReach'];
	'data-testid'?: string;
};

type SelectionProps = {
	selectionType: 'checkbox';
	selectAllLabel: string;
	selectRowLabel: (rowId: string) => string;
	checkIsAllSelected: () => boolean;
	checkIsIndeterminate: () => boolean;
	checkIsRowSelected: (rowId: string) => boolean;
	onSelectAll: () => void;
	onSelect: (rowId: string) => void;
};

// Selection is all-or-nothing: passing `selectionType: 'checkbox'` requires every other
// selection prop too, so a caller can't accidentally render a checkbox with an empty aria-label.
type Props<TRow> = BaseProps<TRow> & ({selectionType?: undefined} | SelectionProps);

function SortableTable<TRow>(props: Props<TRow>) {
	const {
		columns,
		rows,
		rowKey,
		size = 'md',
		isFetching = false,
		emptyState,
		onSort,
		onVerticalScrollStartReach,
		onVerticalScrollEndReach,
		'data-testid': dataTestId,
	} = props;
	const selection = props.selectionType === 'checkbox' ? props : undefined;
	const scrollableContainerRef = useRef<HTMLDivElement | null>(null);
	const hasScrollHandlers = onVerticalScrollStartReach !== undefined || onVerticalScrollEndReach !== undefined;
	const columnCount = columns.length + (selection !== undefined ? 1 : 0);

	const tableBody = (
		<TableBody>
			{rows.length === 0 && emptyState !== undefined ? (
				<TableRow>
					<TableCell colSpan={columnCount}>
						<EmptyStateContainer>{emptyState}</EmptyStateContainer>
					</TableCell>
				</TableRow>
			) : (
				rows.map((row) => {
					const id = rowKey(row);
					return (
						<TableRow key={id}>
							{selection !== undefined && (
								<TableSelectRow
									id={`select-row-${id}`}
									name={`select-row-${id}`}
									aria-label={selection.selectRowLabel(id)}
									checked={selection.checkIsRowSelected(id)}
									onSelect={() => selection.onSelect(id)}
								/>
							)}
							{columns.map((col) => (
								<TableCell key={col.key}>{col.render(row)}</TableCell>
							))}
						</TableRow>
					);
				})
			)}
		</TableBody>
	);

	const innerTable = (
		<>
			{isFetching && <LoadingOverlay aria-hidden />}
			<Table size={size} isSortable>
				<TableHead>
					<TableRow>
						{selection !== undefined && (
							<TableSelectAll
								id="select-all-rows"
								name="select-all-rows"
								aria-label={selection.selectAllLabel}
								checked={selection.checkIsAllSelected()}
								indeterminate={selection.checkIsIndeterminate()}
								onSelect={() => selection.onSelectAll()}
							/>
						)}
						{columns.map((col) =>
							col.sortKey !== undefined ? (
								<ColumnHeader
									key={col.key}
									sortKey={col.sortKey}
									label={col.label}
									isDefault={col.isDefault}
									defaultOrder={col.defaultOrder}
									onSort={onSort}
								/>
							) : (
								<TableHeader key={col.key} isSortable={false}>
									{col.label}
								</TableHeader>
							),
						)}
					</TableRow>
				</TableHead>
				{hasScrollHandlers ? (
					<InfiniteScroller
						onVerticalScrollStartReach={onVerticalScrollStartReach}
						onVerticalScrollEndReach={onVerticalScrollEndReach}
						scrollableContainerRef={scrollableContainerRef}
					>
						{tableBody}
					</InfiniteScroller>
				) : (
					tableBody
				)}
			</Table>
		</>
	);

	if (hasScrollHandlers) {
		return (
			<ScrollContainer ref={scrollableContainerRef} data-testid={dataTestId}>
				{innerTable}
			</ScrollContainer>
		);
	}

	return <TableContainer data-testid={dataTestId}>{innerTable}</TableContainer>;
}

export {SortableTable};
