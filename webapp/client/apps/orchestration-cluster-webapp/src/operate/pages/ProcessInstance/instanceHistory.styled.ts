/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
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
	HistoryTreeNode,
	HistoryIcon,
	HistoryBar,
	HistoryState,
	HistoryName,
	HistoryMetadata,
	HistoryTimestamp,
	HistoryStatus,
	BottomPanel,
	TabPanel,
};
