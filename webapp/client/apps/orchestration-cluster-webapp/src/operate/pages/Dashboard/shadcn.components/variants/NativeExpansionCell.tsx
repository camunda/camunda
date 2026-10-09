/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Button, DataTable, useC4Dictionary, type DataTableColumn} from '@camunda/design-system';
import {ChevronDown, ChevronRight} from '@camunda/design-system/icons';
import {cn} from '#/shared/cn';
import {ExpandableListSkeletonRow} from '#/operate/pages/Dashboard/shadcn.components/ExpandableListSkeletonRow';
import type {
	ExpandableListRow,
	ExpandableListVariantProps,
} from '#/operate/pages/Dashboard/shadcn.components/ExpandableList.types';

type DisplayRow =
	| {kind: 'row'; row: ExpandableListRow}
	| {kind: 'detail'; parentId: string; content: React.ReactNode}
	| {kind: 'skeleton'; id: string; testId: string; label: string};

function getDisplayRowId(displayRow: DisplayRow): string {
	if (displayRow.kind === 'row') {
		return `row-${displayRow.row.id}`;
	}

	if (displayRow.kind === 'detail') {
		return `detail-${displayRow.parentId}`;
	}

	return displayRow.id;
}

const NativeExpansionCell: React.FC<ExpandableListVariantProps> = ({
	header,
	rows,
	renderExpansion,
	isPending,
	isFetchingNextPage,
	isFetchingPreviousPage,
	loadingNextPage,
	loadingPreviousPage,
}) => {
	const {t} = useC4Dictionary();
	const [expandedIds, setExpandedIds] = useState<ReadonlySet<string>>(new Set());

	const toggle = (id: string) => {
		setExpandedIds((current) => {
			const next = new Set(current);
			if (next.has(id)) {
				next.delete(id);
			} else {
				next.add(id);
			}
			return next;
		});
	};

	const displayRows: DisplayRow[] = [];

	if (isFetchingPreviousPage) {
		displayRows.push({
			kind: 'skeleton',
			id: '__skeleton-previous',
			testId: loadingPreviousPage.testId,
			label: loadingPreviousPage.label,
		});
	}

	for (const row of rows) {
		displayRows.push({kind: 'row', row});

		if (expandedIds.has(row.id)) {
			const content = renderExpansion(row);
			if (content !== null) {
				displayRows.push({kind: 'detail', parentId: row.id, content});
			}
		}
	}

	if (isFetchingNextPage) {
		displayRows.push({
			kind: 'skeleton',
			id: '__skeleton-next',
			testId: loadingNextPage.testId,
			label: loadingNextPage.label,
		});
	}

	const columns: DataTableColumn<DisplayRow>[] = [
		{
			id: 'content',
			header,
			cell: ({row: displayRow}) => {
				const data = displayRow.original;

				if (data.kind === 'detail') {
					return (
						<div
							data-row-kind="detail"
							// The DS `<TableCell>` wrapping this div keeps its own
							// `px-3` (size="sm") padding, which would otherwise leave
							// an unpainted gap between this background and the
							// table's edges. `-mx-3` cancels it so the background
							// bleeds full width, then `px-4` re-applies the desired
							// inset for the content itself.
							className="bg-neutral-background-medium -mx-3 px-4 py-4"
						>
							{data.content}
						</div>
					);
				}

				if (data.kind === 'skeleton') {
					return <ExpandableListSkeletonRow testId={data.testId} label={data.label} />;
				}

				const isExpanded = expandedIds.has(data.row.id);
				const expansionContent = renderExpansion(data.row);

				return (
					<div className="flex items-center gap-2">
						{expansionContent !== null ? (
							<Button
								type="button"
								variant="ghost"
								size="icon-sm"
								aria-expanded={isExpanded}
								aria-label={isExpanded ? t('dataTable.collapseRow') : t('dataTable.expandRow')}
								onClick={() => toggle(data.row.id)}
							>
								{isExpanded ? <ChevronDown aria-hidden /> : <ChevronRight aria-hidden />}
							</Button>
						) : (
							<div aria-hidden className="size-8 shrink-0" />
						)}
						<div className="min-w-0 flex-1">{data.row.content}</div>
					</div>
				);
			},
		},
	];

	return (
		<div
			className={cn(
				'contents',
				'[&_[data-slot=table]]:table-fixed',
				'[&_[data-slot=table-container]]:overflow-visible!',
				'[&_[data-slot=table-container]]:rounded-none! [&_[data-slot=table-container]]:border-0! [&_[data-slot=table-container]]:shadow-none!',
				'[&_[data-slot=table-header]]:sticky [&_[data-slot=table-header]]:top-0 [&_[data-slot=table-header]]:z-10',
				// See `ComposedCell`'s equivalent comment: the DS `<TableRow>`'s
				// `border-b` row divider gets its color from the same `.c4-ui *`
				// reset that `ExpandableList`'s outer border relied on, and is
				// subject to the same Chromium invalidation bug on remount.
				'[&_[data-slot=table-row]]:border-b-[var(--border)]',
			)}
		>
			<DataTable<DisplayRow>
				size="sm"
				columns={columns}
				data={displayRows}
				aria-label={header}
				getRowId={getDisplayRowId}
				loading={isPending}
			/>
		</div>
	);
};

export {NativeExpansionCell};
