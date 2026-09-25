/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {DataTable, type DataTableColumn} from '@camunda/design-system';
import type {ExpandableListRow, ExpandableListVariantProps} from '../ExpandableList.types';
import './ComposedCell.css';

const ComposedCell: React.FC<ExpandableListVariantProps> = ({header, rows, renderExpansion, isPending}) => {
	const columns: DataTableColumn<ExpandableListRow>[] = [
		{
			id: 'content',
			header: () => <span className="sr-only">{header}</span>,
			cell: ({row}) => <div data-expandable={renderExpansion(row.original) !== null}>{row.original.content}</div>,
		},
	];

	return (
		<div className="composed-cell-table contents">
			<DataTable<ExpandableListRow>
				size="sm"
				columns={columns}
				data={rows}
				expansion={renderExpansion}
				aria-label={header}
				getRowId={(row) => row.id}
				loading={isPending}
			/>
		</div>
	);
};

export {ComposedCell};
