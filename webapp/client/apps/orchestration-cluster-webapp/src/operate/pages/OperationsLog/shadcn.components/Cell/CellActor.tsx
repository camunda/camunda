/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {InlineCode, Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';
import type {AuditLog, AuditLogActorType} from '@camunda/camunda-api-zod-schemas/8.11/audit-log';
import {ActorIcon} from '#/operate/shared/OperationsLogDetailsModal/shadcn.components/ActorIcon';
import {AiAgentIcon} from '#/operate/shared/OperationsLogDetailsModal/AiAgentIcon';
import {McpIcon} from '#/operate/shared/OperationsLogDetailsModal/McpIcon';
import {hasActorIcon} from '#/operate/shared/OperationsLogDetailsModal/operationsLogUtils';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';

type Props = {
	item: AuditLog;
};

type IconTooltipProps = {
	label: string;
	detail?: string | null;
	children: React.ReactNode;
};

const IconTooltip: React.FC<IconTooltipProps> = ({label, detail, children}) => (
	<Tooltip>
		<TooltipTrigger asChild>
			<span role="img" aria-label={label} tabIndex={0} className="inline-flex shrink-0 items-center">
				{children}
			</span>
		</TooltipTrigger>
		<TooltipContent side="bottom" align="start">
			<div className="flex flex-col gap-2">
				<span>{label}</span>
				{detail && <InlineCode className="break-all">{detail}</InlineCode>}
			</div>
		</TooltipContent>
	</Tooltip>
);

const CellActor: React.FC<Props> = ({item}) => {
	const {t} = useTranslation();

	const getActorLabel = (actor: AuditLogActorType | 'AGENT') =>
		actor === 'AGENT'
			? t('operate.operationsLog.actorTooltip.aiAgentOnBehalfOf', {actorType: item.actorType?.toLowerCase() ?? ''})
			: spaceAndCapitalize(actor);

	if (!item.actorId) {
		return '-';
	}

	return (
		<div className="flex items-center gap-2">
			{hasActorIcon(item) && (
				<IconTooltip label={getActorLabel(item.actorType)} detail={item.actorId}>
					<ActorIcon auditLog={item} aria-hidden="true" className="size-4" data-testid="actor-icon" />
				</IconTooltip>
			)}
			{item.agentElementId && (
				<IconTooltip label={getActorLabel('AGENT')} detail={item.agentElementId}>
					<AiAgentIcon aria-hidden="true" className="size-4" data-testid="agent-icon" />
				</IconTooltip>
			)}
			{item.inboundChannelType === 'MCP' && (
				<IconTooltip label={t('operate.operationsLog.actorTooltip.mcpToolCall')} detail={item.inboundChannelToolName}>
					<McpIcon aria-hidden="true" className="size-4" data-testid="mcp-icon" />
				</IconTooltip>
			)}
			{item.actorId}
		</div>
	);
};

export {CellActor};
