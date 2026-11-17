/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {
	TableCell as BaseTableCell,
	Table as BaseTable,
	TableContainer as BaseTableContainer,
	TableExpandRow as BaseTableExpandRow,
	TableExpandedRow as BaseTableExpandedRow,
	TableHead as BaseTableHead,
} from '@carbon/react';

const TableContainer = styled(BaseTableContainer)`
	&& {
		padding-top: 0;
	}
`;

const Table = styled(BaseTable)`
	table-layout: fixed;
	// keep the data column readable on narrow viewports: overflow horizontally
	// instead of collapsing the label to zero width.
	min-width: 22rem;
`;

const TableHead = styled(BaseTableHead)`
	display: none;
`;

const TableExpandRow = styled(BaseTableExpandRow)<{$isExpandable: boolean}>`
	${({$isExpandable}) =>
		!$isExpandable &&
		css`
			button {
				display: none;
			}
		`}

	&& td:first-child {
		width: var(--cds-spacing-08);
	}

	&& button {
		width: var(--cds-spacing-07);
		height: var(--cds-spacing-07);
		flex-shrink: 0;
	}
`;

const noVerticalPadding = css`
	&& {
		padding-top: 0;
		padding-bottom: 0;
	}
`;

const ExpandableTableCell = styled(BaseTableCell)`
	${noVerticalPadding}
`;

const TableExpandedRow = styled(BaseTableExpandedRow)`
	&& td {
		padding-top: 0;
		padding-bottom: 0;
		// give the expanded child rows the layer background
		background-color: var(--cds-layer);
	}
`;

export {ExpandableTableCell, Table, TableContainer, TableExpandRow, TableExpandedRow, TableHead};
