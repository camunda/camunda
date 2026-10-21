/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled, {css} from 'styled-components';
import {Add, Error, WarningAltFilled} from '@carbon/react/icons';
import {TreeNode} from '@carbon/react';
import {styles} from '@carbon/type';

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
// Mirrors the legacy Operate tree: the label spans the full row (Carbon offsets it with a negative margin),
// so the state icon sits in a fixed gutter, the caret and leaf icons are shifted by one spacing-05 step and
// selected rows keep the active left bar even after focus moves away.
const HistoryTreeNode = styled(TreeNode)`
	> .cds--tree-node__label {
		height: 2rem;
		> .cds--tree-parent-node__toggle {
			flex-shrink: 0;
			margin-inline-start: var(--cds-spacing-05);
		}
		> .cds--tree-node__label__details {
			inline-size: 100%;
		}
	}
	&.cds--tree-node--selected > .cds--tree-node__label::before {
		position: absolute;
		inset-block-start: 0;
		inset-inline-start: 0;
		block-size: 100%;
		inline-size: 4px;
		background-color: var(--cds-interactive);
		content: '';
	}
`;
const HistoryIcon = styled.span<{$leaf: boolean}>`
	display: flex;
	flex-shrink: 0;
	margin-inline-start: ${({$leaf}) => ($leaf ? 'var(--cds-spacing-05)' : '0')};
`;
// Overflow stays inside the bar so Carbon never wraps the label in its ellipsis tooltip button.
const HistoryBar = styled.span`
	display: flex;
	align-items: center;
	gap: var(--cds-spacing-05);
	inline-size: 100%;
	min-inline-size: 0;
	overflow: hidden;
`;
const HistoryState = styled.span`
	position: absolute;
	inset-block: 0;
	inset-inline-start: var(--cds-spacing-05);
	display: flex;
	align-items: center;
`;
const HistoryName = styled.span`
	min-width: 0;
	margin-inline-start: var(--cds-spacing-03);
	overflow: hidden;
	white-space: nowrap;
	text-overflow: ellipsis;
`;
const HistoryMetadata = styled.span`
	display: inline-flex;
	align-items: center;
	gap: var(--cds-spacing-05);
	flex-shrink: 0;
`;
const HistoryTimestamp = styled.span`
	${styles.label01}
	padding: 0 var(--cds-spacing-03);
	background: var(--cds-layer-02);
	border-radius: 2px;
	white-space: nowrap;
`;
const HistoryStatus = styled.li`
	padding-inline-start: var(--cds-spacing-05);
`;
const HistoryModificationIcons = styled.span`
	display: inline-flex;
	align-items: center;
	gap: var(--cds-spacing-03);
	flex-shrink: 0;
	margin-inline-start: auto;
	padding-inline-end: var(--cds-spacing-03);
`;
const disabledIconStyles = css`
	color: var(--cds-icon-disabled);
`;
const HistoryAddIcon = styled(Add)`
	${disabledIconStyles}
`;
const HistoryCancelIcon = styled(Error)`
	${disabledIconStyles}
`;
const HistoryWarningIcon = styled(WarningAltFilled)`
	color: var(--cds-support-warning);
`;
const SearchControls = styled.div`
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	gap: var(--cds-spacing-04);
	padding: var(--cds-spacing-05);
	border-bottom: solid 1px var(--cds-border-subtle-01);
`;
const SearchRow = styled.div`
	display: flex;
	align-items: center;
	gap: var(--cds-spacing-03);
	width: 100%;
	.cds--search {
		flex: 1;
		min-width: 0;
	}
`;
const FilterSwitcher = styled.div`
	display: inline-flex;
	gap: var(--cds-spacing-01);
	padding: var(--cds-spacing-01);
	border-radius: 6px;
	background-color: var(--cds-layer-accent-01);
`;
const FilterSwitcherButton = styled.button`
	${styles.label01};
	appearance: none;
	border: none;
	cursor: pointer;
	padding: var(--cds-spacing-02) var(--cds-spacing-04);
	border-radius: 4px;
	background-color: transparent;
	color: var(--cds-text-secondary);
	box-shadow: none;
	&[aria-pressed='true'] {
		background-color: var(--cds-layer-01);
		color: var(--cds-text-primary);
		box-shadow: 0 1px 2px var(--cds-shadow);
	}
	&:hover {
		background-color: var(--cds-layer-hover-01);
	}
	&[aria-pressed='true']:hover {
		background-color: var(--cds-layer-01);
	}
	&:focus-visible {
		outline: 2px solid var(--cds-focus);
		outline-offset: -2px;
	}
`;
const SearchResultsScroll = styled.div`
	overflow-y: auto;
	overflow-anchor: none;
	flex: 1;
	min-height: 0;
`;
// Search results render at tree depth 0; the extra spacing-06 lines their icons up with depth-1 leaves in the tree.
const SearchResultIcon = styled.span`
	display: flex;
	flex-shrink: 0;
	margin-inline-start: calc(var(--cds-spacing-05) + var(--cds-spacing-06));
`;
const ResultsCount = styled.output`
	position: absolute;
	width: 1px;
	height: 1px;
	padding: 0;
	margin: -1px;
	overflow: hidden;
	clip: rect(0, 0, 0, 0);
	white-space: nowrap;
	border: 0;
`;
const EmptyResults = styled.div`
	display: flex;
	align-items: center;
	justify-content: center;
	flex: 1;
	padding: var(--cds-spacing-06);
`;
const DimmableResults = styled.div<{$dimmed: boolean}>`
	display: flex;
	flex-direction: column;
	flex: 1;
	min-height: 0;
	transition: opacity 150ms ease;
	opacity: ${({$dimmed}) => ($dimmed ? 0.5 : 1)};
	pointer-events: ${({$dimmed}) => ($dimmed ? 'none' : 'auto')};
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
	overflow: hidden;
	background-color: var(--cds-layer);
`;

export {
	HistoryModificationIcons,
	HistoryAddIcon,
	HistoryCancelIcon,
	HistoryWarningIcon,
	HistoryPanel,
	HistoryHeader,
	HistoryScroll,
	HistoryTreeNode,
	HistoryIcon,
	HistoryBar,
	HistoryState,
	HistoryName,
	HistoryMetadata,
	HistoryTimestamp,
	HistoryStatus,
	SearchControls,
	SearchRow,
	FilterSwitcher,
	FilterSwitcherButton,
	SearchResultsScroll,
	SearchResultIcon,
	ResultsCount,
	EmptyResults,
	DimmableResults,
	BottomPanel,
	TabPanel,
};
