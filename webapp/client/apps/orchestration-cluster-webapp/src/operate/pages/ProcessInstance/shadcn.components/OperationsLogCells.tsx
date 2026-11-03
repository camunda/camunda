/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {createLink} from '@tanstack/react-router';
import {useTranslation} from 'react-i18next';
import {IconButton, InlineCode, Link, Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';
import {Info} from '@camunda/design-system/icons';
import type {AuditLog, AuditLogActorType} from '@camunda/camunda-api-zod-schemas/8.11/audit-log';
import {ActorIcon} from '#/operate/shared/OperationsLogDetailsModal/shadcn.components/ActorIcon';
import {AiAgentIcon} from '#/operate/shared/OperationsLogDetailsModal/AiAgentIcon';
import {McpIcon} from '#/operate/shared/OperationsLogDetailsModal/McpIcon';
import {OperationsLogResultIcon} from '#/operate/shared/OperationsLogDetailsModal/shadcn.components/OperationsLogResultIcon';
import {hasActorIcon, mapToCellEntityKeyData} from '#/operate/shared/OperationsLogDetailsModal/operationsLogUtils';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';

type DetailsModalState = {isOpen: boolean; auditLog?: AuditLog};

const EntityLink = createLink<React.FC<React.ComponentProps<'a'>>>(Link);

const IconTooltip: React.FC<{content: React.ReactNode; children: React.ReactNode}> = ({content, children}) => (
	<Tooltip>
		<TooltipTrigger asChild>
			<span className="inline-flex" tabIndex={0}>
				{children}
			</span>
		</TooltipTrigger>
		<TooltipContent side="bottom" align="start">
			<div className="flex flex-col gap-1">{content}</div>
		</TooltipContent>
	</Tooltip>
);

const CellResult: React.FC<{item: AuditLog}> = ({item}) => (
	<>
		<OperationsLogResultIcon state={item.result} data-testid={`${item.auditLogKey}-icon`} />
		<span className="sr-only">{spaceAndCapitalize(item.result)}</span>
	</>
);

const CellActor: React.FC<{item: AuditLog}> = ({item}) => {
	const {t} = useTranslation();

	if (!item.actorId) {
		return '-';
	}

	const getTooltipContent = (actor: AuditLogActorType | 'AGENT') => (
		<>
			<span>
				{actor === 'AGENT'
					? t('operate.operationsLog.actorTooltip.aiAgentOnBehalfOf', {actorType: item.actorType?.toLowerCase() ?? ''})
					: spaceAndCapitalize(actor)}
			</span>
			<InlineCode>{actor === 'AGENT' ? item.agentElementId : item.actorId}</InlineCode>
		</>
	);

	return (
		<div className="flex items-center gap-2">
			{hasActorIcon(item) && (
				<IconTooltip content={getTooltipContent(item.actorType)}>
					<ActorIcon auditLog={item} width={16} height={16} data-testid="actor-icon" />
				</IconTooltip>
			)}
			{item.agentElementId && (
				<IconTooltip content={getTooltipContent('AGENT')}>
					<AiAgentIcon data-testid="agent-icon" />
				</IconTooltip>
			)}
			{item.inboundChannelType === 'MCP' && (
				<IconTooltip
					content={
						<>
							<span>{t('operate.operationsLog.actorTooltip.mcpToolCall')}</span>
							{item.inboundChannelToolName && <InlineCode>{item.inboundChannelToolName}</InlineCode>}
						</>
					}
				>
					<McpIcon data-testid="mcp-icon" />
				</IconTooltip>
			)}
			{item.actorId}
		</div>
	);
};

const CellEntityKey: React.FC<{item: AuditLog}> = ({item}) => {
	const {t} = useTranslation();
	const {linkLabel, label, name} = mapToCellEntityKeyData(t, item);
	const linkProps = {title: linkLabel, 'aria-label': linkLabel};

	const renderKey = () => {
		if (item.entityType === 'USER_TASK') {
			return (
				<EntityLink to="/tasklist/$userTaskKey" params={{userTaskKey: item.entityKey}} {...linkProps}>
					{label}
				</EntityLink>
			);
		}

		if (item.entityType === 'PROCESS_INSTANCE') {
			return (
				<EntityLink
					to="/operate/processes/$processInstanceId"
					params={{processInstanceId: item.entityKey}}
					{...linkProps}
				>
					{label}
				</EntityLink>
			);
		}

		if (item.entityType === 'BATCH' && item.batchOperationKey) {
			return (
				<EntityLink
					to="/operate/batch-operations/$batchOperationKey"
					params={{batchOperationKey: String(item.batchOperationKey)}}
					{...linkProps}
				>
					{label}
				</EntityLink>
			);
		}

		if (item.entityType === 'DECISION' && item.operationType === 'EVALUATE') {
			return (
				<EntityLink
					to="/operate/decisions/$decisionInstanceId"
					params={{decisionInstanceId: item.entityKey}}
					{...linkProps}
				>
					{label}
				</EntityLink>
			);
		}

		return label;
	};

	return (
		<div>
			<div>{renderKey()}</div>
			{item.entityDescription?.trim() || <em>{name}</em>}
		</div>
	);
};

const CellComment: React.FC<{item: AuditLog; setDetailsModal: (state: DetailsModalState) => void}> = ({
	item,
	setDetailsModal,
}) => {
	const {t} = useTranslation();

	return (
		<IconButton
			variant="ghost"
			size="sm"
			label={t('operate.operationsLog.comment.openDetails')}
			tooltipSide="bottom"
			icon={Info}
			onClick={() => setDetailsModal({isOpen: true, auditLog: item})}
		/>
	);
};

export type {DetailsModalState};
export {CellActor, CellComment, CellEntityKey, CellResult};
