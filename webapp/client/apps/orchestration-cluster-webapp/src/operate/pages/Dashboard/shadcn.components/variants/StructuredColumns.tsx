/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Badge, DataTable, Text, type DataTableColumn} from '@camunda/design-system';
import type {ExpandableListRow, ExpandableListVariantProps} from '../ExpandableList.types';

/**
 * Candidate row shape: real columns instead of one composed cell.
 *
 * Makes the list an actual table — the counts become addressable data with their
 * own headers, and the name column can be sorted. The trade-off is losing the
 * ratio bar, which conveys the active-to-incident proportion at a glance.
 *
 * `activeCount` is absent on incident-only lists, so the Active column is
 * dropped entirely rather than rendered as a column of blanks.
 */
const StructuredColumns: React.FC<ExpandableListVariantProps> = ({header, rows, renderExpansion}) => {
	const {t} = useTranslation();
	const hasActiveCounts = rows.some((row) => row.activeCount !== undefined);

	const columns: DataTableColumn<ExpandableListRow>[] = [
		{
			id: 'name',
			accessorFn: (row) => (typeof row.name === 'string' ? row.name : ''),
			header,
			cell: ({row}) => (
				<Text as="span" variant="label-md-strong" className="block min-w-0 truncate">
					{row.original.name}
				</Text>
			),
		},
		...(hasActiveCounts
			? [
					{
						id: 'active',
						accessorFn: (row: ExpandableListRow) => row.activeCount ?? 0,
			header: t('operate.dashboard.listActiveColumnHeader'),
						meta: {align: 'right' as const},
						cell: ({row}: {row: {original: ExpandableListRow}}) => {
							const {activeCount} = row.original;
							return (
								<div className="flex justify-end">
									{activeCount === undefined ? null : (
										<Badge variant={activeCount > 0 ? 'success' : 'neutral'}>{activeCount}</Badge>
									)}
								</div>
							);
						},
					},
				]
			: []),
		{
			id: 'incidents',
			accessorFn: (row) => row.incidentsCount,
			header: t('operate.dashboard.listIncidentsColumnHeader'),
			meta: {align: 'right'},
			cell: ({row}) => (
				<div className="flex justify-end">
					<Badge variant={row.original.incidentsCount > 0 ? 'danger' : 'neutral'}>
						{row.original.incidentsCount}
					</Badge>
				</div>
			),
		},
	];

	return (
		<DataTable<ExpandableListRow>
			size="sm"
			columns={columns}
			data={rows}
			expansion={renderExpansion}
			sorting
			aria-label={header}
			getRowId={(row) => row.id}
		/>
	);
};

export {StructuredColumns};
