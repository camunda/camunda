/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {InlineCode, Text} from '@camunda/design-system';
import type {AuditLog} from '@camunda/camunda-api-zod-schemas/8.11/audit-log';
import {ActorIcon} from '#/operate/shared/OperationsLogDetailsModal/shadcn.components/ActorIcon';
import {AiAgentIcon} from '#/operate/shared/OperationsLogDetailsModal/AiAgentIcon';
import {McpIcon} from '#/operate/shared/OperationsLogDetailsModal/McpIcon';
import {hasActorIcon} from '#/operate/shared/OperationsLogDetailsModal/operationsLogUtils';
import {spaceAndCapitalize} from '#/operate/shared/utils/spaceAndCapitalize';
import {IconTooltip} from './IconTooltip';

const CellActor: React.FC<{item: AuditLog}> = ({item}) => {
	const {t} = useTranslation();

	if (!item.actorId) {
		return (
			<>
				<Text aria-hidden="true">-</Text>
				<Text className="sr-only">{t('operate.operationsLog.noActor')}</Text>
			</>
		);
	}

	const actorLabel = spaceAndCapitalize(item.actorType ?? '');
	const agentLabel = t('operate.operationsLog.actorTooltip.aiAgentOnBehalfOf', {
		actorType: item.actorType?.toLowerCase() ?? '',
	});
	const mcpLabel = t('operate.operationsLog.actorTooltip.mcpToolCall');

	return (
		<div className="flex items-center gap-2">
			{hasActorIcon(item) && (
				<IconTooltip
					label={actorLabel}
					content={
						<>
							<Text>{actorLabel}</Text>
							<InlineCode>{item.actorId}</InlineCode>
						</>
					}
				>
					<ActorIcon auditLog={item} width={16} height={16} aria-hidden="true" />
				</IconTooltip>
			)}
			{item.agentElementId && (
				<IconTooltip
					label={agentLabel}
					content={
						<>
							<Text>{agentLabel}</Text>
							<InlineCode>{item.agentElementId}</InlineCode>
						</>
					}
				>
					<AiAgentIcon aria-hidden="true" />
				</IconTooltip>
			)}
			{item.inboundChannelType === 'MCP' && (
				<IconTooltip
					label={mcpLabel}
					content={
						<>
							<Text>{mcpLabel}</Text>
							{item.inboundChannelToolName && <InlineCode>{item.inboundChannelToolName}</InlineCode>}
						</>
					}
				>
					<McpIcon aria-hidden="true" />
				</IconTooltip>
			)}
			<Text>{item.actorId}</Text>
		</div>
	);
};

export {CellActor};
