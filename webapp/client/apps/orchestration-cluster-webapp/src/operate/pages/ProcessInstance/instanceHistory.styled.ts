/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {Button} from '@carbon/react';

const HistoryPanel = styled.section`
	height: 100%;
	display: flex;
	flex-direction: column;
	background: var(--cds-layer-01);
`;
const HistoryHeader = styled.div`
	display: flex;
	align-items: center;
	gap: var(--cds-spacing-05);
	padding: var(--cds-spacing-04);
	flex-wrap: wrap;
	h2 {
		font-size: var(--cds-body-compact-01-font-size);
	}
`;
const HistoryScroll = styled.div`
	overflow: auto;
	flex: 1;
	min-height: 0;
`;
const HistoryChildren = styled.ul`
	list-style: none;
	padding: 0;
	margin: 0;
`;
const HistoryRow = styled.div<{$selected: boolean; $depth: number; $foldable: boolean}>`
	position: relative;
	display: flex;
	align-items: center;
	min-height: 32px;
	padding-inline-start: ${({$depth, $foldable}) =>
		`calc(${$foldable ? 2 : 3} * var(--cds-spacing-05) + ${$depth} * var(--cds-spacing-06))`};
	background: ${({$selected}) => ($selected ? 'var(--cds-layer-selected-01)' : 'transparent')};
	color: var(--cds-text-secondary);
	&:hover {
		background: ${({$selected}) => ($selected ? 'var(--cds-layer-selected-hover-01)' : 'var(--cds-layer-hover-01)')};
		color: var(--cds-text-primary);
	}
	&:focus-within {
		outline: 2px solid var(--cds-focus);
		outline-offset: -2px;
	}
	${({$selected}) =>
		$selected &&
		`
			color: var(--cds-text-primary);
			&::before {
				position: absolute;
				inset-block: 0;
				inset-inline-start: 0;
				width: 4px;
				background: var(--cds-interactive);
				content: '';
			}
		`}
`;
const HistoryState = styled.span`
	position: absolute;
	inset-inline-start: var(--cds-spacing-05);
	display: flex;
	align-items: center;
`;
const HistoryToggle = styled(Button)`
	width: 24px;
	min-width: 24px;
	padding-inline: var(--cds-spacing-02) 0;
	justify-content: flex-start;
	&:focus {
		outline: none;
		box-shadow: none;
	}
`;
const RowSelection = styled.button`
	display: flex;
	align-items: center;
	gap: calc(var(--cds-spacing-03) + var(--cds-spacing-02));
	border: 0;
	background: transparent;
	color: inherit;
	font: inherit;
	cursor: pointer;
	text-align: left;
	padding: 3px var(--cds-spacing-05) 3px 0;
	flex: 1;
	min-width: 0;
	svg {
		flex-shrink: 0;
	}
	&:focus {
		outline: none;
	}
`;
const BottomPanel = styled.div`
	height: 100%;
	min-height: 0;
	.HorizontalPanel:first-child {
		min-width: max(420px, 25%);
	}
	.HorizontalPanel:last-child {
		min-width: 25%;
	}
`;
const TabPanel = styled.div`
	height: 100%;
	display: flex;
	flex-direction: column;
	> nav {
		display: flex;
		gap: var(--cds-spacing-05);
		padding: var(--cds-spacing-05);
	}
	> div {
		flex: 1;
		min-height: 0;
	}
	a {
		color: var(--cds-link-primary);
	}
	a[aria-current='page'] {
		font-weight: 600;
	}
`;

export {
	HistoryPanel,
	HistoryHeader,
	HistoryScroll,
	HistoryChildren,
	HistoryRow,
	HistoryState,
	HistoryToggle,
	RowSelection,
	BottomPanel,
	TabPanel,
};
