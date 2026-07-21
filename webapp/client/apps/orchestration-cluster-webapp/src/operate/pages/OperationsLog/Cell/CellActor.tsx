/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Tooltip} from '@carbon/react';
import {useTranslation} from 'react-i18next';
import type {AuditLog, AuditLogActorType} from '@camunda/camunda-api-zod-schemas/8.10/audit-log';
import {ActorIcon} from '#/operate/shared/OperationsLogDetailsModal/ActorIcon';
import {AiAgentIcon} from '#/operate/shared/OperationsLogDetailsModal/AiAgentIcon';
import {McpIcon} from '#/operate/shared/OperationsLogDetailsModal/McpIcon';
import {hasActorIcon} from '#/operate/shared/OperationsLogDetailsModal/operationsLogUtils';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';
import {AuthorTooltip, OperationLogName, TooltipCodeSnippet} from '../styled';

type Props = {
	item: AuditLog;
};

const CellActor: React.FC<Props> = ({item}) => {
	const {t} = useTranslation();

	const getTooltipActorContent = (actor: AuditLogActorType | 'AGENT') => {
		const label =
			actor === 'AGENT'
				? t('operate.operationsLog.actorTooltip.aiAgentOnBehalfOf', {actorType: item.actorType.toLowerCase()})
				: spaceAndCapitalize(actor);
		return (
			<AuthorTooltip>
				<span>{label}</span>
				<TooltipCodeSnippet wrapText>{actor === 'AGENT' ? item.agentElementId : item.actorId}</TooltipCodeSnippet>
			</AuthorTooltip>
		);
	};

	if (!item.actorId) {
		return '-';
	}

	return (
		<OperationLogName>
			{hasActorIcon(item) && (
				<Tooltip autoAlign align="bottom-start" description={getTooltipActorContent(item.actorType)}>
					<ActorIcon auditLog={item} data-testid="actor-icon" />
				</Tooltip>
			)}
			{item.agentElementId && (
				<Tooltip autoAlign align="bottom-start" description={getTooltipActorContent('AGENT')}>
					<AiAgentIcon data-testid="agent-icon" />
				</Tooltip>
			)}
			{item.inboundChannelType === 'MCP' && (
				<Tooltip
					autoAlign
					align="bottom-start"
					description={
						<AuthorTooltip>
							<span>{t('operate.operationsLog.actorTooltip.mcpToolCall')}</span>
							{item.inboundChannelToolName && (
								<TooltipCodeSnippet wrapText>{item.inboundChannelToolName}</TooltipCodeSnippet>
							)}
						</AuthorTooltip>
					}
				>
					<McpIcon data-testid="mcp-icon" />
				</Tooltip>
			)}
			{item.actorId}
		</OperationLogName>
	);
};

export {CellActor};
