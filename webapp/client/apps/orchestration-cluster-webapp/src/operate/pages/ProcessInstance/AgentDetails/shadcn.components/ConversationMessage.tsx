/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {IconButton, Text} from '@camunda/design-system';
import {Maximize2} from '@camunda/design-system/icons';
import type {
	AgentInstanceHistoryItemMetrics,
	AgentInstanceHistoryRole,
	AgentInstanceMessageContent,
	AgentInstanceToolCall,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {CopyButton} from '#/operate/shared/CopyButton/shadcn.components/CopyButton';
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

const ACCENT_CLASS_BY_ACTOR: Record<Actor, string> = {
	SYSTEM: 'border-l-[var(--neutral-border-strong)]',
	USER: 'border-l-[var(--chart-blue)]',
	ASSISTANT: 'border-l-[var(--chart-purple)]',
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
		<div className="group/content flex max-h-[360px] gap-3">
			{children}
			<div className="flex opacity-0 transition-opacity has-[:focus-visible]:opacity-100! group-hover/content:opacity-100!">
				<IconButton
					variant="ghost"
					size="sm"
					icon={Maximize2}
					label={t('operate.processInstance.details.agent.expand')}
					onClick={(event) => onExpandClick(event.currentTarget)}
				/>
				<CopyButton value={value} hasIconOnly tooltipAlignment="end" />
			</div>
		</div>
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
		<article
			className={`flex flex-col gap-2 rounded border-l-[3px] bg-[var(--neutral-background-medium)] p-4 ${ACCENT_CLASS_BY_ACTOR[actor]}`}
			aria-label={title}
		>
			<div className="flex items-center gap-3">
				<Text as="span" variant="label-sm" className="font-semibold uppercase text-[var(--neutral-foreground-subtle)]">
					{t(LABEL_KEY_BY_ACTOR[actor])}
				</Text>
				<MessageMetrics metrics={metrics} />
			</div>
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
								<pre className="flex-1 overflow-auto whitespace-pre-wrap break-words rounded bg-[var(--neutral-background-strong)] p-3 font-mono text-sm">
									{value}
								</pre>
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
								<div className="flex-1 overflow-y-auto text-sm text-foreground">
									<MarkdownMessage content={entry.text} />
								</div>
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
		</article>
	);
};

export {ConversationMessage};
