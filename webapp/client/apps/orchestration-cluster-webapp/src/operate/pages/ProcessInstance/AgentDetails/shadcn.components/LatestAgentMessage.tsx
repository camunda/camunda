/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {Skeleton} from '@camunda/design-system';
import type {AgentInstanceStatus} from '@camunda/camunda-api-zod-schemas/8.11';
import {latestAgentMessageQuery} from '../agentHistory.queries';
import {ConversationMessage} from './ConversationMessage';
import {useRefetchOnStatusChange} from '../useRefetchOnStatusChange';

type LatestAgentMessageProps = {
	agentInstanceKey: string;
	agentInstanceStatus: AgentInstanceStatus;
};

const LatestAgentMessage: React.FC<LatestAgentMessageProps> = ({agentInstanceKey, agentInstanceStatus}) => {
	const {t} = useTranslation();
	const {data, status, refetch} = useQuery(latestAgentMessageQuery(agentInstanceKey, agentInstanceStatus));

	useRefetchOnStatusChange(agentInstanceStatus, true, refetch);

	if (status === 'pending') {
		return (
			<div className="space-y-2">
				<Skeleton className="h-4 w-24" />
				<Skeleton className="h-4 w-full" />
				<Skeleton className="h-4 w-4/5" />
			</div>
		);
	}

	if (status === 'error') {
		return (
			<span className="text-center text-sm text-muted-foreground">
				{t('operate.processInstance.details.agent.latestMessageLoadError')}
			</span>
		);
	}

	if (data === null || data.role !== 'ASSISTANT') {
		return (
			<span className="text-center text-sm text-muted-foreground">
				{t('operate.processInstance.details.agent.noAgentMessage')}
			</span>
		);
	}

	return <ConversationMessage actor={data.role} content={data.content} toolCalls={data.toolCalls} />;
};

export {LatestAgentMessage};
