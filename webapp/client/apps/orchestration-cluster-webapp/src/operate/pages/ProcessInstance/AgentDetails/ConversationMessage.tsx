/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Button} from '@carbon/react';
import {Maximize} from '@carbon/react/icons';
import type {
	AgentInstanceHistoryItemMetrics,
	AgentInstanceHistoryRole,
	AgentInstanceMessageContent,
	AgentInstanceToolCall,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {CopyButton} from '#/operate/shared/CopyButton/CopyButton';
import {
	ActorLabel,
	MessageActions,
	MessageBlock,
	MessageContainer,
	MessageHeader,
	ObjectContent,
	TextContent,
} from './conversation.styled';
import {DocumentAttachments} from './DocumentAttachments';
import {MarkdownMessage} from './MarkdownMessage';
import {MessageDetailsModal} from './MessageDetailsModal';
import {MessageMetrics} from './MessageMetrics';
import {ReasoningNote} from './ReasoningNote';
import {ToolCallAttachments} from './ToolCallAttachments';

type Actor = Exclude<AgentInstanceHistoryRole, 'TOOL_RESULT' | 'CONFIGURATION'> | 'SYSTEM';

type MessageDetails = {
	language: 'markdown' | 'json';
	content: string;
};

const TITLE_KEY_BY_ACTOR: Record<Actor, string> = {
	SYSTEM: 'operate.processInstance.details.agent.systemPromptMessage',
	USER: 'operate.processInstance.details.agent.userMessage',
	ASSISTANT: 'operate.processInstance.details.agent.assistantMessage',
};

const LABEL_KEY_BY_ACTOR: Record<Actor, string> = {
	SYSTEM: 'operate.processInstance.details.agent.actorSystem',
	USER: 'operate.processInstance.details.agent.actorUser',
	ASSISTANT: 'operate.processInstance.details.agent.actorAssistant',
};

function isPlainObject(value: unknown): value is Record<string, unknown> {
	if (typeof value !== 'object' || value === null || Array.isArray(value)) {
		return false;
	}

	const prototype = Object.getPrototypeOf(value);
	return prototype === Object.prototype || prototype === null;
}

function isReasoning(value: unknown): value is Record<string, unknown> {
	return isPlainObject(value) && value['camunda.agenticai.content.type'] === 'reasoning';
}

function getReasoningText(value: Record<string, unknown>): string | null {
	const text = value['text'];
	return typeof text !== 'string' || text.trim() === '' ? null : text;
}

type MessageContentProps = {
	value: string;
	children: React.ReactNode;
	onExpandClick: (trigger: HTMLButtonElement) => void;
};

const MessageContent: React.FC<MessageContentProps> = ({value, children, onExpandClick}) => {
	const {t} = useTranslation();

	return (
		<MessageBlock>
			{children}
			<MessageActions>
				<Button
					kind="ghost"
					size="sm"
					renderIcon={Maximize}
					iconDescription={t('operate.processInstance.details.agent.expand')}
					hasIconOnly
					onClick={(event) => onExpandClick(event.currentTarget)}
				/>
				<CopyButton value={value} hasIconOnly tooltipAlignment="end" />
			</MessageActions>
		</MessageBlock>
	);
};

type ConversationMessageProps = {
	actor: Actor;
	content: AgentInstanceMessageContent[];
	metrics?: AgentInstanceHistoryItemMetrics | null;
	toolCalls?: AgentInstanceToolCall[];
};

const ConversationMessage: React.FC<ConversationMessageProps> = ({actor, content, metrics = null, toolCalls = []}) => {
	const {t} = useTranslation();
	const [messageDetails, setMessageDetails] = useState<MessageDetails | null>(null);
	const triggerRef = useRef<HTMLButtonElement | null>(null);
	const openDetails = (trigger: HTMLButtonElement, details: MessageDetails) => {
		triggerRef.current = trigger;
		setMessageDetails(details);
	};
	const title = t(TITLE_KEY_BY_ACTOR[actor]);

	return (
		<MessageContainer $actor={actor} aria-label={title}>
			<MessageHeader>
				<ActorLabel>{t(LABEL_KEY_BY_ACTOR[actor])}</ActorLabel>
				<MessageMetrics metrics={metrics} />
			</MessageHeader>
			{content.map((entry, index) => {
				switch (entry.contentType) {
					case 'DOCUMENT':
						return null;
					case 'OBJECT': {
						if (actor === 'ASSISTANT' && isReasoning(entry.object)) {
							const reasoningText = getReasoningText(entry.object);
							return reasoningText === null ? null : <ReasoningNote key={index} reasoning={reasoningText} />;
						}

						const value = JSON.stringify(entry.object, null, 2);
						return (
							<MessageContent
								key={index}
								value={value}
								onExpandClick={(trigger) => openDetails(trigger, {language: 'json', content: value})}
							>
								<ObjectContent>{value}</ObjectContent>
							</MessageContent>
						);
					}
					case 'TEXT':
						return (
							<MessageContent
								key={index}
								value={entry.text}
								onExpandClick={(trigger) => openDetails(trigger, {language: 'markdown', content: entry.text})}
							>
								<TextContent>
									<MarkdownMessage content={entry.text} />
								</TextContent>
							</MessageContent>
						);
				}
			})}
			<DocumentAttachments
				content={content}
				modalTitleSuffix={t('operate.processInstance.details.agent.conversationMessageDocumentsSuffix')}
			/>
			<ToolCallAttachments toolCalls={toolCalls} />
			{messageDetails !== null && (
				<MessageDetailsModal
					title={title}
					language={messageDetails.language}
					content={messageDetails.content}
					onClose={() => {
						setMessageDetails(null);
						triggerRef.current?.focus();
					}}
				/>
			)}
		</MessageContainer>
	);
};

export {ConversationMessage};
