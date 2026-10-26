/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {ActorLabel, MessageContainer, MessageHeader} from './conversation.styled';

const EDITOR_BASIS = '400px';
const COLUMN_GAP = '1rem';

const ToolActions = styled.div`
	grid-area: actions;
	display: flex;
	opacity: 0;

	&:has(:focus-visible) {
		opacity: 1;
	}
`;

const ToolResultContainer = styled(MessageContainer)`
	display: grid;
	align-items: start;
	column-gap: var(--cds-spacing-03);
	grid-template-columns: 1fr auto;
	grid-template-areas:
		'header actions'
		'content actions';

	&:hover ${ToolActions} {
		opacity: 1;
	}
`;

const ToolHeader = styled(MessageHeader)`
	grid-area: header;

	& > svg {
		color: var(--cds-icon-secondary);
	}
`;

const ToolLabel = styled(ActorLabel)`
	text-transform: initial;
`;

const ResultPreview = styled.p`
	grid-area: content;
	font-family: var(--cds-code-01-font-family);
	font-size: var(--cds-code-01-font-size);
	font-weight: var(--cds-code-01-font-weight);
	line-height: var(--cds-code-01-line-height);
	letter-spacing: var(--cds-code-01-letter-spacing);
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
	color: var(--cds-text-secondary);
	max-inline-size: 100ch;
`;

const Description = styled.p`
	&& {
		margin-block-start: 0;
	}
	color: var(--cds-text-secondary);
`;

const Columns = styled.div`
	display: flex;
	flex-wrap: wrap;
	gap: ${COLUMN_GAP};
	container-type: inline-size;
`;

const Column = styled.section`
	flex: 1 1 ${EDITOR_BASIS};
	display: grid;
	grid-template-rows: auto 1fr;
	grid-template-columns: 1fr auto;
	align-items: center;
	gap: var(--cds-spacing-03);

	& > :last-child {
		align-self: start;
		grid-column: 1 / -1;
	}
`;

const EditorContainer = styled.div`
	block-size: 40vh;

	@container (max-inline-size: calc(2 * ${EDITOR_BASIS} + ${COLUMN_GAP})) {
		block-size: 25vh;
	}
`;

const ColumnLabel = styled.h3`
	font-size: var(--cds-heading-compact-01-font-size);
	font-weight: var(--cds-heading-compact-01-font-weight);
	line-height: var(--cds-heading-compact-01-line-height);
	letter-spacing: var(--cds-heading-compact-01-letter-spacing);
	color: var(--cds-text-primary);
`;

const EmptyHint = styled.span`
	font-size: var(--cds-body-compact-01-font-size);
	line-height: var(--cds-body-compact-01-line-height);
	letter-spacing: var(--cds-body-compact-01-letter-spacing);
	font-weight: var(--cds-body-compact-01-font-weight);
	color: var(--cds-text-secondary);
`;

export {
	Column,
	ColumnLabel,
	Columns,
	Description,
	EditorContainer,
	EmptyHint,
	ResultPreview,
	ToolActions,
	ToolHeader,
	ToolLabel,
	ToolResultContainer,
};
