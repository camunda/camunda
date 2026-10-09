/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {DataTable, type DataTableColumn} from '@camunda/design-system';
import {cn} from '#/shared/cn';
import {ExpandableListSkeletonRow} from '../ExpandableListSkeletonRow';
import type {ExpandableListRow, ExpandableListVariantProps} from '../ExpandableList.types';

type DisplayRow = {kind: 'row'; row: ExpandableListRow} | {kind: 'skeleton'; id: string; testId: string; label: string};

const ComposedCell: React.FC<ExpandableListVariantProps> = ({
	header,
	rows,
	renderExpansion,
	isPending,
	isFetchingNextPage,
	isFetchingPreviousPage,
	loadingNextPage,
	loadingPreviousPage,
}) => {
	const displayRows: DisplayRow[] = [
		...(isFetchingPreviousPage
			? [
					{
						kind: 'skeleton' as const,
						id: '__skeleton-previous',
						testId: loadingPreviousPage.testId,
						label: loadingPreviousPage.label,
					},
				]
			: []),
		...rows.map((row): DisplayRow => ({kind: 'row', row})),
		...(isFetchingNextPage
			? [
					{
						kind: 'skeleton' as const,
						id: '__skeleton-next',
						testId: loadingNextPage.testId,
						label: loadingNextPage.label,
					},
				]
			: []),
	];

	const columns: DataTableColumn<DisplayRow>[] = [
		{
			id: 'content',
			header,
			cell: ({row: displayRow}) => {
				const data = displayRow.original;

				if (data.kind === 'skeleton') {
					return (
						<div data-expandable={false}>
							<ExpandableListSkeletonRow testId={data.testId} label={data.label} />
						</div>
					);
				}

				return <div data-expandable={renderExpansion(data.row) !== null}>{data.row.content}</div>;
			},
		},
	];

	return (
		<div
			className={cn(
				'contents',
				'[&_[data-slot=table]]:table-fixed',
				'[&_[data-slot=table-row]>[data-slot=table-head]:first-child]:w-10',
				'[&_tr:has([data-expandable=false])_[data-slot=data-table-expand-toggle]]:hidden!',
				'[&_[data-slot=table-container]]:overflow-visible!',
				'[&_[data-slot=table-container]]:rounded-none! [&_[data-slot=table-container]]:border-0! [&_[data-slot=table-container]]:shadow-none!',
				'[&_[data-slot=table-header]]:sticky [&_[data-slot=table-header]]:top-0 [&_[data-slot=table-header]]:z-10',
				// The DS `<TableRow>`'s row-divider (`border-b`) only sets
				// width/style; its color comes from the same `.c4-ui *` reset
				// as `ExpandableList`'s outer border (see that component's
				// comment) and is subject to the same Chromium invalidation bug
				// after this subtree is detached/reattached. Re-asserting the
				// color via a direct class selector avoids it here too.
				'[&_[data-slot=table-row]]:border-b-[var(--border)]',
			)}
		>
			<DataTable<DisplayRow>
				size="sm"
				columns={columns}
				data={displayRows}
				expansion={(displayRow) => (displayRow.kind === 'row' ? renderExpansion(displayRow.row) : null)}
				aria-label={header}
				getRowId={(displayRow) => (displayRow.kind === 'row' ? displayRow.row.id : displayRow.id)}
				loading={isPending}
			/>
		</div>
	);
};

export {ComposedCell};
