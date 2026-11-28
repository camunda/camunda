/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useId} from 'react';
import {useTranslation} from 'react-i18next';
import {Heading} from '@camunda/design-system';
import {Wrench} from '@camunda/design-system/icons';
import type {AgentInstanceToolCall} from '@camunda/camunda-api-zod-schemas/8.11';

const ToolCallAttachments: React.FC<{toolCalls: AgentInstanceToolCall[]}> = ({toolCalls}) => {
	const {t} = useTranslation();
	const id = useId();

	if (toolCalls.length === 0) {
		return null;
	}

	return (
		<div className="mt-4 inline-flex flex-wrap items-center gap-2">
			<Heading as="h3" id={id} variant="label-sm" className="text-[var(--neutral-foreground-subtle)]">
				{t('operate.processInstance.details.agent.toolCalls')}
			</Heading>
			<ul aria-labelledby={id} className="contents list-none p-0">
				{toolCalls.map((toolCall) => (
					<li
						key={toolCall.toolCallId}
						title={toolCall.toolName}
						className="inline-flex items-center gap-2 rounded-full border border-border px-3 py-0.5 text-xs"
					>
						<Wrench className="size-3 text-[var(--neutral-foreground-subtle)]" aria-hidden />
						{toolCall.toolName}
					</li>
				))}
			</ul>
		</div>
	);
};

export {ToolCallAttachments};
