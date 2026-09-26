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
import type {ExpandableListRow, ExpandableListVariantProps} from '../ExpandableList.types';

type DisplayRow = {kind: 'row'; row: ExpandableListRow} | {kind: 'detail'; parentId: string; content: React.ReactNode};

function getDisplayRowId(displayRow: DisplayRow): string {
	return displayRow.kind === 'row' ? displayRow.row.id : `${displayRow.parentId}__expansion`;
}

const NativeExpansionCell: React.FC<ExpandableListVariantProps> = ({header, rows, renderExpansion, isPending}) => {
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
	for (const row of rows) {
		displayRows.push({kind: 'row', row});

		if (expandedIds.has(row.id)) {
			const content = renderExpansion(row);
			if (content !== null) {
				displayRows.push({kind: 'detail', parentId: row.id, content});
			}
		}
	}

	const columns: DataTableColumn<DisplayRow>[] = [
		{
			id: 'content',
			header: () => <span className="sr-only">{header}</span>,
			cell: ({row: displayRow}) => {
				const data = displayRow.original;

				if (data.kind === 'detail') {
					return <div className="bg-neutral-background-medium px-4 py-4">{data.content}</div>;
				}

				const isExpanded = expandedIds.has(data.row.id);
				const expansionContent = renderExpansion(data.row);

				return (
					<div className="flex items-center gap-2">
						{expansionContent !== null && (
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
						)}
						<div className="flex-1">{data.row.content}</div>
					</div>
				);
			},
		},
	];

	return (
		<DataTable<DisplayRow>
			size="sm"
			columns={columns}
			data={displayRows}
			aria-label={header}
			getRowId={getDisplayRowId}
			loading={isPending}
		/>
	);
};

export {NativeExpansionCell};
