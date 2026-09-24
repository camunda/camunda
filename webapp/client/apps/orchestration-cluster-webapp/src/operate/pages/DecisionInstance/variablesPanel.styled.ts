/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {SkeletonText, TabPanel} from '@carbon/react';
import {styles} from '@carbon/type';
import {StructuredList} from '#/operate/shared/StructuredList/StructuredList';
import {EmptyMessage} from '#/operate/shared/EmptyMessage/EmptyMessage';
import {ErrorMessage} from '#/operate/shared/ErrorMessage/ErrorMessage';

const VariablesContainer = styled.div`
	display: flex;
	flex-direction: column;
	height: 100%;
	width: 100%;
	background-color: var(--cds-layer);
	overflow: hidden;

	.cds--tabs {
		flex: none;
		box-shadow: inset 0 -1px 0 0 var(--cds-border-subtle-01);
	}

	.cds--tab-content {
		flex: 1 1 0;
		min-height: 0;
	}
`;

const Content = styled.section`
	flex: 1;
	min-height: 0;
`;

const Panel = styled(TabPanel)`
	padding: 0;
	overflow: hidden;
`;

const InputOutputContainer = styled.div`
	height: 100%;
`;

const InputOutputSection = styled.section`
	height: 100%;
	display: grid;
	grid-template-rows: auto 1fr;
	overflow: hidden;
`;

const SkeletonContainer = styled.div`
	position: relative;
	height: 100%;
`;

const SkeletonList = styled.ul`
	overflow: hidden;
	width: 100%;
	position: absolute;
`;

const SkeletonRow = styled.li`
	margin: var(--cds-spacing-05);
	display: flex;
	gap: var(--cds-spacing-05);
`;

const SkeletonCell = styled(SkeletonText)`
	margin: 0;
`;

const Title = styled.h2`
	${styles.productiveHeading02}
	color: var(--cds-text-secondary);
	margin: var(--cds-spacing-05) 0 0 var(--cds-spacing-05);
`;

const InputOutputTable = styled(StructuredList)`
	margin-top: var(--cds-spacing-05);
`;

const Message = styled.div`
	align-self: center;
	justify-self: center;
`;

const ErrorMessageContainer = styled(Message)`
	grid-row: 1 / -1;
`;

const PanelEmptyMessage = styled(EmptyMessage)`
	max-width: unset;
`;

const PanelErrorMessage = styled(ErrorMessage)`
	max-width: unset;
`;

const ResultContainer = styled.div`
	height: 100%;
	display: flex;
	justify-content: center;
	align-items: center;
	position: relative;
	overflow: hidden;

	.cds--loading-overlay {
		position: absolute;
	}
`;

const JsonViewer = styled.div`
	position: absolute;
	inset: 0;
	height: 100%;
	width: 100%;
`;

export {
	VariablesContainer,
	Content,
	Panel,
	InputOutputContainer,
	InputOutputSection,
	SkeletonContainer,
	SkeletonList,
	SkeletonRow,
	SkeletonCell,
	Title,
	InputOutputTable,
	Message,
	ErrorMessageContainer,
	PanelEmptyMessage,
	PanelErrorMessage,
	ResultContainer,
	JsonViewer,
};
