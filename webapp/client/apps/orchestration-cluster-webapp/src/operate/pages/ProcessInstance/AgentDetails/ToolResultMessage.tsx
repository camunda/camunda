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
import {Maximize, Tools} from '@carbon/react/icons';
import type {
	AgentInstanceMessageContent,
	AgentInstanceToolCall,
	AgentTool,
} from '@camunda/camunda-api-zod-schemas/8.11';
import {DocumentAttachments} from './DocumentAttachments';
import {getToolCallResult} from './getToolCallResult';
import {ResultPreview, ToolActions, ToolHeader, ToolLabel, ToolResultContainer} from './toolResult.styled';
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
		<ToolResultContainer
			$actor="TOOL_RESULT"
			aria-label={t('operate.processInstance.details.agent.toolResultLabel', {name: toolCall.toolName})}
		>
			<ToolHeader>
				<Tools size={12} />
				<ToolLabel>{toolCall.toolName}</ToolLabel>
			</ToolHeader>
			{resultPreview !== null && <ResultPreview>{resultPreview}</ResultPreview>}
			<DocumentAttachments
				content={content}
				modalTitleSuffix={t('operate.processInstance.details.agent.toolResultDocumentsSuffix')}
			/>
			<ToolActions>
				<Button
					kind="ghost"
					size="sm"
					renderIcon={Maximize}
					iconDescription={t('operate.processInstance.details.agent.expand')}
					tooltipAlignment="end"
					hasIconOnly
					ref={triggerRef}
					onClick={() => setIsExpanded(true)}
				/>
			</ToolActions>
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
		</ToolResultContainer>
	);
};

export {ToolResultMessage};
