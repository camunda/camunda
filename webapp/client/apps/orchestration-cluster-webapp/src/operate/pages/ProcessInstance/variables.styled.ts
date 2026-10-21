/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {InlineLoading as BaseInlineLoading} from '@carbon/react';
import {styles} from '@carbon/type';
import {StructuredList as BaseStructuredList} from '#/operate/shared/StructuredList/StructuredList';

const Content = styled.div`
	position: relative;
	height: 100%;
	display: flex;
	flex-direction: column;

	.cds--loading-overlay {
		position: absolute;
	}
`;

const EmptyMessageContainer = styled.div`
	display: flex;
	align-items: center;
	justify-content: center;
	height: 100%;
`;

const FilterSwitcherContainer = styled.div`
	display: flex;
	flex-direction: column;
	align-items: flex-start;
	gap: var(--cds-spacing-04);
	padding: 0 var(--cds-spacing-05) var(--cds-spacing-05);
	border-bottom: solid 1px var(--cds-border-subtle-01);
`;

const VariablesSearch = styled.div`
	width: 100%;
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

const StructuredList = styled(BaseStructuredList)`
	padding: 0 var(--cds-spacing-05) var(--cds-spacing-05);

	[role='table'] {
		table-layout: fixed;
	}
`;

const VariableName = styled.div`
	${styles.bodyShort01};
	margin: var(--cds-spacing-02) 0;
	text-overflow: ellipsis;
	overflow: hidden;
	white-space: nowrap;
`;

const Operations = styled.div`
	display: flex;
	justify-content: end;
	min-width: var(--cds-spacing-10);
`;

const InlineLoading = styled(BaseInlineLoading)`
	justify-content: end;
`;

export {
	Content,
	EmptyMessageContainer,
	FilterSwitcherContainer,
	VariablesSearch,
	DimmableResults,
	StructuredList,
	VariableName,
	Operations,
	InlineLoading,
};
