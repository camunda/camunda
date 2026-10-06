/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';

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
	li > div > ul {
		padding-left: var(--cds-spacing-06);
	}
`;
const HistoryRow = styled.div<{$selected: boolean}>`
	display: flex;
	min-height: 32px;
	background: ${({$selected}) => ($selected ? 'var(--cds-layer-selected-01)' : 'transparent')};
`;
const RowSelection = styled.button`
	display: flex;
	align-items: center;
	gap: var(--cds-spacing-03);
	border: 0;
	background: transparent;
	color: var(--cds-text-primary);
	font: inherit;
	cursor: pointer;
	text-align: left;
	padding: 3px var(--cds-spacing-03);
	flex: 1;
	min-width: 0;
	svg {
		flex-shrink: 0;
	}
	&:focus-visible {
		outline: 2px solid var(--cds-focus);
		outline-offset: -2px;
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

export {HistoryPanel, HistoryHeader, HistoryScroll, HistoryChildren, HistoryRow, RowSelection, BottomPanel, TabPanel};
