/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {InlineLoading as BaseInlineLoading, Button, Layer, Loading} from '@carbon/react';
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

const Form = styled.form`
	height: 100%;
	display: flex;
	flex-direction: column;
`;

const ModificationAddVariableButton = styled(Button)`
	align-self: flex-end;
`;

const VariablesContainer = styled.div`
	height: 100%;
	position: relative;
`;

const VariablesContent = styled.div`
	position: absolute;
	width: 100%;
	height: 100%;
	top: 0;
	left: 0;
	display: flex;
	flex-direction: column;
	justify-content: space-between;
`;

const FooterContainer = styled.div`
	margin-top: auto;
	border-top: 1px solid var(--cds-border-subtle-01);
`;

const NewVariableLayer = styled(Layer)`
	width: 100%;
	padding: var(--cds-spacing-02) var(--cds-spacing-07) var(--cds-spacing-02) var(--cds-spacing-05);
	display: grid;
	grid-template-columns: 1fr 2fr auto;
	grid-gap: var(--cds-spacing-05);
`;

const SavingLoadingContainer = styled.div`
	display: inline-flex;
	align-items: center;
	justify-content: center;
	width: 2rem;
	height: 2rem;
	flex-shrink: 0;
`;

const SavingLoading = styled(Loading)`
	align-self: center;
`;

export {
	Form,
	ModificationAddVariableButton,
	VariablesContainer,
	VariablesContent,
	FooterContainer,
	NewVariableLayer,
	SavingLoadingContainer,
	SavingLoading,
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
