/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useQuery} from '@tanstack/react-query';
import {useTranslation} from 'react-i18next';
import {SkeletonText} from '@carbon/react';
import type {AgentInstanceStatus} from '@camunda/camunda-api-zod-schemas/8.11';
import {latestAgentMessageQuery} from './agentHistory.queries';
import {ConversationMessage} from './ConversationMessage';
import {StatusHint} from './conversation.styled';
import {useRefetchOnStatusChange} from './useRefetchOnStatusChange';

type LatestAgentMessageProps = {
	agentInstanceKey: string;
	agentInstanceStatus: AgentInstanceStatus;
};

const LatestAgentMessage: React.FC<LatestAgentMessageProps> = ({agentInstanceKey, agentInstanceStatus}) => {
	const {t} = useTranslation();
	const {data, status, refetch} = useQuery(latestAgentMessageQuery(agentInstanceKey, agentInstanceStatus));

	useRefetchOnStatusChange(agentInstanceStatus, true, refetch);

	if (status === 'pending') {
		return <SkeletonText heading paragraph lineCount={3} />;
	}

	if (status === 'error') {
		return <StatusHint>{t('operate.processInstance.details.agent.latestMessageLoadError')}</StatusHint>;
	}

	if (data === null || data.role !== 'ASSISTANT') {
		return <StatusHint>{t('operate.processInstance.details.agent.noAgentMessage')}</StatusHint>;
	}

	return <ConversationMessage actor={data.role} content={data.content} toolCalls={data.toolCalls} />;
};

export {LatestAgentMessage};
