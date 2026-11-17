/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import styled from 'styled-components';
import {Button, ContentSwitcher} from '@carbon/react';
import type {AgentInstanceHistoryRole} from '@camunda/camunda-api-zod-schemas/8.11';

type ActorType = Exclude<AgentInstanceHistoryRole, 'CONFIGURATION'> | 'SYSTEM';

const accentColorByActor: Record<ActorType, string> = {
	SYSTEM: 'var(--cds-status-gray)',
	ASSISTANT: 'var(--cds-status-purple)',
	USER: 'var(--cds-status-blue)',
	TOOL_RESULT: 'var(--cds-status-gray)',
};

const ConversationContainer = styled.div`
	display: flex;
	flex-direction: column;
	gap: var(--cds-spacing-04);

	.cds--btn-set > .cds--btn {
		inline-size: auto;
	}
`;

const Messages = styled.div`
	display: flex;
	flex-direction: inherit;
	gap: inherit;
	transition: opacity 150ms ease;

	&[data-dimmed='true'] {
		opacity: 0.5;
		pointer-events: none;
	}
`;

const StatusHint = styled.span`
	font-size: var(--cds-body-compact-01-font-size);
	font-weight: var(--cds-body-compact-01-font-weight);
	line-height: var(--cds-body-compact-01-line-height);
	letter-spacing: var(--cds-body-compact-01-letter-spacing);
	text-align: center;
	color: var(--cds-text-secondary);
`;

const LoopIterationMarker = styled.span`
	display: inline-flex;
	gap: var(--cds-spacing-04);
	justify-content: center;
	align-items: center;
	font-size: var(--cds-label-01-font-size);
	font-weight: var(--cds-label-01-font-weight);
	line-height: var(--cds-label-01-line-height);
	letter-spacing: var(--cds-label-01-letter-spacing);
	color: var(--cds-text-secondary);

	&::before,
	&::after {
		content: '';
		display: inline-block;
		inline-size: 8ch;
		height: 1px;
		background-color: var(--cds-border-subtle-01);
		border-radius: 1px;
	}
`;

const ShowMoreButton = styled(Button)`
	align-self: center;
`;

const MessageContainer = styled.article<{$actor: ActorType}>`
	display: flex;
	flex-direction: column;
	gap: var(--cds-spacing-02);
	padding: var(--cds-spacing-04);
	border-left: 3px solid ${({$actor}) => accentColorByActor[$actor]};
	background-color: var(--cds-layer-02);
	border-radius: 4px;
`;

const MessageHeader = styled.div`
	display: flex;
	align-items: center;
	gap: var(--cds-spacing-03);
`;

const ActorLabel = styled.h5`
	font-size: var(--cds-label-01-font-size);
	font-weight: var(--cds-heading-compact-01-font-weight);
	line-height: var(--cds-label-01-line-height);
	letter-spacing: var(--cds-label-01-letter-spacing);
	color: var(--cds-text-secondary);
	text-transform: uppercase;
`;

const MessageActions = styled.div`
	display: flex;
	opacity: 0;

	&:has(:focus-visible) {
		opacity: 1;
	}
`;

const MessageBlock = styled.div`
	display: flex;
	gap: var(--cds-spacing-03);
	max-height: 360px;

	&:hover ${MessageActions} {
		opacity: 1;
	}
`;

const TextContent = styled.div`
	flex: 1;
	overflow-y: auto;
	font-size: var(--cds-body-compact-01-font-size);
	font-weight: var(--cds-body-compact-01-font-weight);
	line-height: var(--cds-body-compact-01-line-height);
	letter-spacing: var(--cds-body-compact-01-letter-spacing);
	color: var(--cds-text-primary);
`;

const ObjectContent = styled.pre`
	flex: 1;
	overflow: auto;
	border-radius: 4px;
	padding: var(--cds-spacing-03);
	background-color: var(--cds-layer-03);
	font-family: var(--cds-code-01-font-family);
	font-size: var(--cds-code-01-font-size);
	font-weight: var(--cds-code-01-font-weight);
	line-height: var(--cds-code-01-line-height);
	letter-spacing: var(--cds-code-01-letter-spacing);
	white-space: pre-wrap;
	word-break: break-word;
`;

const ModalContent = styled(TextContent)`
	min-height: 60vh;
	max-height: 70vh;
`;

const ModalToolbar = styled.div`
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding-bottom: var(--cds-spacing-05);
`;

const JsonModalToolbar = styled(ModalToolbar)`
	justify-content: flex-end;
`;

const ViewSwitcher = styled(ContentSwitcher)`
	width: 15rem;
`;

const ReasoningRoot = styled.section`
	display: flex;
	min-width: 0;
	flex: 1;
	flex-direction: column;
	gap: var(--cds-spacing-02);
	padding: var(--cds-spacing-02) 0;
	color: var(--cds-text-secondary);
`;

const ReasoningLabel = styled.span`
	font-size: var(--cds-label-01-font-size);
	font-weight: var(--cds-heading-compact-01-font-weight);
	line-height: var(--cds-label-01-line-height);
	letter-spacing: var(--cds-label-01-letter-spacing);
`;

const ReasoningText = styled.p`
	margin: 0;
	font-size: var(--cds-body-compact-01-font-size);
	line-height: var(--cds-body-compact-01-line-height);
	letter-spacing: var(--cds-body-compact-01-letter-spacing);
	font-style: italic;
	white-space: pre-wrap;
	overflow-wrap: break-word;
`;

const MetricsContainer = styled.div`
	display: flex;
	align-items: center;
	gap: var(--cds-spacing-02);
	--cds-tooltip-padding-block: var(--cds-spacing-02);
	--cds-tooltip-padding-inline: var(--cds-spacing-03);
`;

export {
	ActorLabel,
	ConversationContainer,
	JsonModalToolbar,
	LoopIterationMarker,
	MessageActions,
	MessageBlock,
	MessageContainer,
	MessageHeader,
	Messages,
	MetricsContainer,
	ModalContent,
	ModalToolbar,
	ObjectContent,
	ReasoningLabel,
	ReasoningRoot,
	ReasoningText,
	ShowMoreButton,
	StatusHint,
	TextContent,
	ViewSwitcher,
};
