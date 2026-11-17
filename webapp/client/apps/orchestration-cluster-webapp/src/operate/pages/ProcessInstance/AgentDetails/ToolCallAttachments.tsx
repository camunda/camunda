/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useId} from 'react';
import {useTranslation} from 'react-i18next';
import {Tools} from '@carbon/react/icons';
import type {AgentInstanceToolCall} from '@camunda/camunda-api-zod-schemas/8.11';
import {Attachment, AttachmentsContainer, AttachmentsLabel, AttachmentsList} from './attachments.styled';

const ToolCallAttachments: React.FC<{toolCalls: AgentInstanceToolCall[]}> = ({toolCalls}) => {
	const {t} = useTranslation();
	const id = useId();

	if (toolCalls.length === 0) {
		return null;
	}

	return (
		<AttachmentsContainer>
			<AttachmentsLabel id={id}>{t('operate.processInstance.details.agent.toolCalls')}</AttachmentsLabel>
			<AttachmentsList aria-labelledby={id}>
				{toolCalls.map((toolCall) => (
					<Attachment key={toolCall.toolCallId} title={toolCall.toolName}>
						<Tools size={12} />
						{toolCall.toolName}
					</Attachment>
				))}
			</AttachmentsList>
		</AttachmentsContainer>
	);
};

export {ToolCallAttachments};
