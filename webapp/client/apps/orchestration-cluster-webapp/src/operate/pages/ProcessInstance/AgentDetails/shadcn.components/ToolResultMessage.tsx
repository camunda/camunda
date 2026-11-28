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
import {Maximize2, Wrench} from '@camunda/design-system/icons';
import type {
	AgentInstanceMessageContent,
	AgentInstanceToolCall,
	AgentTool,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {DocumentAttachments} from './DocumentAttachments';
import {getToolCallResult} from '../getToolCallResult';
import {ToolResultModal} from './ToolResultModal';

type ToolResultMessageProps = {
	availableTools: AgentTool[];
	toolCalls: AgentInstanceToolCall[];
	content: AgentInstanceMessageContent[];
};

const ToolResultMessage: React.FC<ToolResultMessageProps> = ({availableTools, toolCalls, content}) => {
	const {t} = useTranslation();
	const [isExpanded, setIsExpanded] = useState(false);
	const triggerRef = useRef<HTMLButtonElement>(null);
	const toolCall = toolCalls[0];

	if (toolCall === undefined) {
		return null;
	}

	const result = getToolCallResult(content);
	const hasDocuments = content.some((item) => item.contentType === 'DOCUMENT');
	const resultPreview =
		result?.preview ?? (hasDocuments ? null : t('operate.processInstance.details.agent.emptyToolResult'));
	const description = availableTools.find((tool) => tool.name === toolCall.toolName)?.description ?? null;

	return (
		<article
			className="group/tool-result rounded border-l-[3px] border-l-[var(--neutral-border-strong)] bg-[var(--neutral-background-medium)] p-4"
			aria-label={t('operate.processInstance.details.agent.toolResultLabel', {name: toolCall.toolName})}
		>
			<div className="grid grid-cols-[1fr_auto] items-start gap-x-3">
				<div className="flex items-center gap-2">
					<Wrench className="size-3 text-[var(--neutral-foreground-subtle)]" aria-hidden />
					<Text as="span" variant="label-sm" className="font-semibold text-[var(--neutral-foreground-subtle)]">
						{toolCall.toolName}
					</Text>
				</div>
				<div className="flex opacity-0 transition-opacity has-[:focus-visible]:opacity-100! group-hover/tool-result:opacity-100!">
					<IconButton
						variant="ghost"
						size="sm"
						icon={Maximize2}
						label={t('operate.processInstance.details.agent.expand')}
						ref={triggerRef}
						onClick={() => setIsExpanded(true)}
					/>
				</div>
			</div>
			{resultPreview !== null && (
				<p className="m-0 mt-2 max-w-[100ch] overflow-hidden text-ellipsis whitespace-nowrap font-mono text-xs text-[var(--neutral-foreground-subtle)]">
					{resultPreview}
				</p>
			)}
			<DocumentAttachments
				content={content}
				modalTitleSuffix={t('operate.processInstance.details.agent.toolResultDocumentsSuffix')}
			/>
			{isExpanded && (
				<ToolResultModal
					toolName={toolCall.toolName}
					description={description}
					input={toolCall.arguments}
					content={content}
					onClose={() => {
						setIsExpanded(false);
						triggerRef.current?.focus();
					}}
				/>
			)}
		</article>
	);
};

export {ToolResultMessage};
