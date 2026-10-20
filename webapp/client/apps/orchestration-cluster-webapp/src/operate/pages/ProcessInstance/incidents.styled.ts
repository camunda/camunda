/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {
	DataTableSkeleton as BaseDataTableSkeleton,
	Layer,
	Stack,
	TableContainer as BaseTableContainer,
	TableExpandRow as BaseTableExpandRow,
	TableExpandedRow as BaseTableExpandedRow,
} from '@carbon/react';

const ERROR_MESSAGE_MIN_WIDTH = '16rem';
const FAILING_ELEMENT_MAX_WIDTH = '12rem';

const Container = styled.section`
	flex: 1;
	min-height: 0;
	display: flex;
	flex-direction: column;
	overflow: hidden;

	// The error message has no natural width, so it takes the space the other columns leave
	// ('width: 100%') without adding its content to the table's minimum width ('max-width: 0').
	td[data-column-key='errorMessage'] {
		width: 100%;
		max-width: 0;
		min-width: ${ERROR_MESSAGE_MIN_WIDTH};
	}

	td[data-column-key='elementName'] {
		max-width: ${FAILING_ELEMENT_MAX_WIDTH};
	}

	td[data-column-key='errorMessage'] .cds--popover-container,
	td[data-column-key='errorMessage'] .cds--tooltip-trigger__wrapper,
	td[data-column-key='elementName'] .cds--popover-container,
	td[data-column-key='elementName'] .cds--tooltip-trigger__wrapper {
		display: block;
		width: 100%;
		min-width: 0;
	}

	td[data-column-key='errorMessage'] .cds--tooltip-content,
	td[data-column-key='elementName'] .cds--tooltip-content {
		white-space: normal;
	}

	td[data-column-key='elementName'] .cds--link {
		display: inline;
	}

	td[data-column-key='operations'] {
		padding-top: 0;
		padding-bottom: 0;
		white-space: normal;
	}
`;

const FilterLayer = styled(Layer)`
	margin-left: auto;
`;

const FilterContainer = styled.div`
	display: flex;
	width: 100%;
	justify-content: flex-end;
	padding: var(--cds-spacing-01) var(--cds-spacing-05);
`;

const FilterStack = styled(Stack)`
	align-items: center;
`;

const ScrollContainer = styled.div`
	position: relative;
	height: 100%;
	background-color: var(--cds-layer);
	overflow-x: auto;
	overflow-y: auto;
	flex: 1 0 0;
`;

const TableContainer = styled(BaseTableContainer)`
	.cds--data-table-content {
		overflow-x: inherit;
	}

	th,
	td {
		white-space: nowrap;
	}
`;

const TableExpandRow = styled(BaseTableExpandRow)<{onClick?: React.MouseEventHandler<HTMLTableRowElement>}>`
	cursor: pointer;

	&:not(:first-child) td {
		border-block-start: none !important;
	}
`;

const TableExpandedRow = styled(BaseTableExpandedRow)`
	td {
		border-block-start: none !important;
	}
`;

const ExpandedContent = styled(Layer)`
	display: flex;
	flex-direction: column;
	gap: var(--cds-spacing-05);
	white-space: normal;
`;

const ExpandedField = styled.div`
	display: flex;
	flex-direction: column;
	gap: var(--cds-spacing-02);
`;

const FieldLabel = styled.span`
	color: var(--cds-text-secondary);
`;

const ErrorMessageCell = styled.div`
	white-space: pre-wrap;
	word-break: break-word;
`;

const TruncatedText = styled.span`
	display: block;
	overflow: hidden;
	white-space: nowrap;
	text-overflow: ellipsis;
`;

const FlexContainer = styled.div`
	display: flex;
	align-items: center;
`;

const EmptyMessageContainer = styled.div`
	display: flex;
	justify-content: center;
	align-items: center;
	height: 100%;
	background-color: var(--cds-layer);
`;

const ChildIncidentContainer = styled(EmptyMessageContainer)`
	padding: var(--cds-spacing-05);
`;

const OperationContainer = styled(Stack)`
	.cds--popover[role='tooltip'] {
		display: none;
	}
`;

const DataTableSkeleton = styled(BaseDataTableSkeleton)`
	tr {
		height: 2rem;
	}
`;

export {
	Container,
	FilterLayer,
	FilterContainer,
	FilterStack,
	ScrollContainer,
	TableContainer,
	TableExpandRow,
	TableExpandedRow,
	ExpandedContent,
	ExpandedField,
	FieldLabel,
	ErrorMessageCell,
	TruncatedText,
	FlexContainer,
	EmptyMessageContainer,
	ChildIncidentContainer,
	OperationContainer,
	DataTableSkeleton,
};
