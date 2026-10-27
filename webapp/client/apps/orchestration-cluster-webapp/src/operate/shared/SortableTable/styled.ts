/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {
	TableContainer as CarbonTableContainer,
	TableRow as CarbonTableRow,
	TableExpandedRow as CarbonTableExpandedRow,
} from '@carbon/react';

const TableContainer = styled(CarbonTableContainer)`
	position: relative;
	background-color: var(--cds-layer);
`;

const STICKY_RIGHT_CLASS = 'sortable-table-sticky-right';

const ScrollContainer = styled.div<{$stickyHeader?: boolean}>`
	position: relative;
	height: 100%;
	background-color: var(--cds-layer);
	overflow-y: auto;
	flex: 1 0 0;

	${({$stickyHeader}) =>
		$stickyHeader &&
		css`
			.cds--data-table-content {
				overflow-x: visible;
			}

			thead {
				position: sticky;
				top: 0;
				z-index: 2;
			}
		`}

	.${STICKY_RIGHT_CLASS} {
		position: sticky;
		inset-inline-end: 0;
		background-color: var(--cds-layer);
		border-inline-start: 1px solid var(--cds-border-subtle);
	}

	th.${STICKY_RIGHT_CLASS}:last-of-type {
		position: sticky;
		text-align: center;
		background-color: var(--cds-layer-accent);

		.cds--table-header-label {
			text-align: center;
		}
	}

	tr:hover > td.${STICKY_RIGHT_CLASS} {
		background-color: var(--cds-layer-hover);
	}
`;

const LoadingOverlay = styled.div`
	position: absolute;
	inset: 0;
	background: var(--cds-layer);
	opacity: 0.5;
	z-index: 1;
	pointer-events: none;
`;

const EmptyStateContainer = styled.div`
	display: flex;
	justify-content: center;
	align-items: center;
	padding: var(--cds-spacing-09) 0;
`;

// Used when the empty state replaces the table entirely (no header row) — fills the whole
// container instead of just padding out a table cell.
const BareEmptyStateContainer = styled.div`
	display: flex;
	justify-content: center;
	align-items: center;
	height: 100%;
`;

const FailureRow = styled(CarbonTableRow)<{$isFailed: boolean; $isSucceeded: boolean}>`
	.cds--table-expand__svg {
		transform: rotate(0deg);
	}

	.cds--table-expand__button[aria-expanded='true'] .cds--table-expand__svg {
		transform: rotate(90deg);
	}

	${({$isFailed}) =>
		$isFailed &&
		css`
			box-shadow: inset 3px 0 0 var(--cds-support-error);

			&&.cds--expandable-row > td {
				border-block-end: none;
				box-shadow: none;
			}

			&.cds--expandable-row:hover + tr[data-child-row] td {
				box-shadow: inset 3px 0 0 var(--cds-support-error);
			}
		`}

	${({$isSucceeded}) =>
		$isSucceeded &&
		css`
			box-shadow: inset 3px 0 0 var(--cds-support-success);
		`}
`;

const FailureDetailRow = styled(CarbonTableExpandedRow)`
	box-shadow: inset 3px 0 0 var(--cds-support-error);

	&&& td {
		border-block-start: none;
		box-shadow: inset 3px 0 0 var(--cds-support-error);
		white-space: pre-wrap;
		overflow-wrap: anywhere;
	}
`;

export {
	TableContainer,
	ScrollContainer,
	LoadingOverlay,
	EmptyStateContainer,
	BareEmptyStateContainer,
	FailureRow,
	FailureDetailRow,
	STICKY_RIGHT_CLASS,
};
