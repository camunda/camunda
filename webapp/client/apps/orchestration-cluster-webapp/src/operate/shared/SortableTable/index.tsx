/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Fragment, useId, useRef, useState} from 'react';
import {
	Table,
	TableBody,
	TableCell,
	TableExpandHeader,
	TableHead,
	TableHeader,
	TableRow,
	TableSelectAll,
	TableSelectRow,
} from '@carbon/react';
import {ChevronRight} from '@carbon/react/icons';
import {
	TableContainer,
	ScrollContainer,
	LoadingOverlay,
	EmptyStateContainer,
	BareEmptyStateContainer,
	FailureRow,
	FailureDetailRow,
} from './styled';
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
	// Renders the empty state on its own, without the table/column headers, instead of inside a
	// full-width table row. Opt-in because most tables keep their headers visible when empty.
	hideHeaderWhenEmpty?: boolean;
	onSort?: (sortKey: string, order: 'asc' | 'desc') => void;
	onVerticalScrollStartReach?: React.ComponentProps<typeof InfiniteScroller>['onVerticalScrollStartReach'];
	onVerticalScrollEndReach?: React.ComponentProps<typeof InfiniteScroller>['onVerticalScrollEndReach'];
	rowOperationError?: (row: TRow) => {message: string; expandLabel: string} | null;
	failureDetailsLabel?: string;
	expansionScope?: string;
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
		hideHeaderWhenEmpty = false,
		onSort,
		onVerticalScrollStartReach,
		onVerticalScrollEndReach,
		rowOperationError,
		failureDetailsLabel,
		expansionScope,
		'data-testid': dataTestId,
	} = props;
	const selection = props.selectionType === 'checkbox' ? props : undefined;
	const scrollableContainerRef = useRef<HTMLDivElement | null>(null);
	const [expansionState, setExpansionState] = useState(() => ({scope: expansionScope, ids: new Set<string>()}));
	const detailIdPrefix = useId();
	if (expansionState.scope !== expansionScope) {
		setExpansionState({scope: expansionScope, ids: new Set()});
	}
	const hasScrollHandlers = onVerticalScrollStartReach !== undefined || onVerticalScrollEndReach !== undefined;
	const columnCount = columns.length + (selection !== undefined ? 1 : 0) + (rowOperationError !== undefined ? 1 : 0);

	if (rows.length === 0 && emptyState !== undefined && hideHeaderWhenEmpty) {
		const emptyContent = <BareEmptyStateContainer>{emptyState}</BareEmptyStateContainer>;

		if (hasScrollHandlers) {
			return <ScrollContainer data-testid={dataTestId}>{emptyContent}</ScrollContainer>;
		}

		return <TableContainer data-testid={dataTestId}>{emptyContent}</TableContainer>;
	}

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
					const error = rowOperationError?.(row);
					const isExpanded = error != null && expansionState.scope === expansionScope && expansionState.ids.has(id);
					const cells = (
						<>
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
						</>
					);
					return (
						<Fragment key={id}>
							<FailureRow
								$isFailed={error != null}
								className={
									rowOperationError === undefined
										? undefined
										: isExpanded
											? 'cds--parent-row cds--expandable-row'
											: 'cds--parent-row'
								}
								data-parent-row={rowOperationError !== undefined ? true : undefined}
							>
								{rowOperationError !== undefined && (
									<TableCell className="cds--table-expand" headers={`${detailIdPrefix}-expand`}>
										{error != null && (
											<button
												type="button"
												className="cds--table-expand__button"
												aria-label={error.expandLabel}
												aria-expanded={isExpanded}
												aria-controls={isExpanded ? `${detailIdPrefix}-${id}` : undefined}
												onClick={() =>
													setExpansionState((current) => {
														const next = new Set(current.scope === expansionScope ? current.ids : []);
														if (next.has(id)) {
															next.delete(id);
														} else {
															next.add(id);
														}
														return {scope: expansionScope, ids: next};
													})
												}
											>
												<ChevronRight aria-hidden className="cds--table-expand__svg" />
											</button>
										)}
									</TableCell>
								)}
								{cells}
							</FailureRow>
							{isExpanded && (
								<FailureDetailRow id={`${detailIdPrefix}-${id}`} colSpan={columnCount}>
									{error.message}
								</FailureDetailRow>
							)}
						</Fragment>
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
						{rowOperationError !== undefined && (
							<TableExpandHeader id={`${detailIdPrefix}-expand`}>
								<span className="cds--visually-hidden">{failureDetailsLabel}</span>
							</TableExpandHeader>
						)}
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
