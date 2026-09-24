/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {DataTable, type DataTableColumn} from '@camunda/design-system';
import type {ExpandableListRow, ExpandableListVariantProps} from '../ExpandableList.types';

/**
 * Carbon's row shape: one opaque content cell per row, into which the consumer
 * composes the name, the counts and the ratio bar together.
 */
const ComposedCell: React.FC<ExpandableListVariantProps> = ({header, rows, renderExpansion}) => {
	// DataTable always renders a header row; it's reduced to an sr-only label here
	// since this list has none in Carbon. Recorded for design review, see
	// docs/migration/operate-dashboard-ds-gaps.md.
	const columns: DataTableColumn<ExpandableListRow>[] = [
		{
			id: 'content',
			header: () => <span className="sr-only">{header}</span>,
			cell: ({row}) => row.original.content,
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

export {ComposedCell};
