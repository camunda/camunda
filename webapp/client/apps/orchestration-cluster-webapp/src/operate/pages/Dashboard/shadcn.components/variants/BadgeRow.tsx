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
 * Candidate row shape: name plus discrete coloured count badges, no ratio bar.
 *
 * The most compact of the candidates. Severity is read from badge colour rather
 * than by judging a bar's proportion, which removes the guesswork but also the
 * sense of scale the bar gives.
 *
 * Still a single content column, so like `composed` it keeps the sr-only header.
 */
const BadgeRow: React.FC<ExpandableListVariantProps> = ({header, rows, renderExpansion}) => {
	const {t} = useTranslation();

	const columns: DataTableColumn<ExpandableListRow>[] = [
		{
			id: 'content',
			header: () => <span className="sr-only">{header}</span>,
			cell: ({row}) => {
				const {name, activeCount, incidentsCount} = row.original;

				return (
					<div className="flex min-w-0 items-center gap-3 py-1">
						<Text as="span" variant="label-md-strong" className="min-w-0 flex-1 truncate">
							{name}
						</Text>
						<div className="flex shrink-0 items-center gap-2">
							{activeCount !== undefined && (
								<Badge variant={activeCount > 0 ? 'success' : 'neutral'}>
									{t('operate.dashboard.listActiveCountBadge', {count: activeCount})}
								</Badge>
							)}
							<Badge variant={incidentsCount > 0 ? 'danger' : 'neutral'}>
								{t('operate.dashboard.listIncidentsCountBadge', {count: incidentsCount})}
							</Badge>
						</div>
					</div>
				);
			},
		},
	];

	return (
		<DataTable<ExpandableListRow>
			size="sm"
			columns={columns}
			data={rows}
			expansion={renderExpansion}
			aria-label={header}
			getRowId={(row) => row.id}
		/>
	);
};

export {BadgeRow};
