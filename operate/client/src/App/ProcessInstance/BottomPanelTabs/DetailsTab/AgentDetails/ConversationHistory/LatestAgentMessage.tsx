/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useRef} from 'react';
import {SkeletonText} from '@carbon/react';
import {useLatestAgentMessage} from 'modules/queries/agentInstances/useLatestAgentMessage';
import {ConversationMessage} from '../ConversationMessage';
import {StatusHint} from './styled';
import type {AgentInstanceStatus} from '@camunda/camunda-api-zod-schemas/8.10';
import {isActiveAgentInstanceStatus} from 'modules/queries/agentInstances/agentInstanceStatus';

type LatestAgentMessageProps = {
  agentInstanceKey: string;
  agentInstanceStatus: AgentInstanceStatus;
};

const LatestAgentMessage: React.FC<LatestAgentMessageProps> = ({
  agentInstanceKey,
  agentInstanceStatus,
}) => {
  const {data, status, refetch, isEnabled} = useLatestAgentMessage(
    agentInstanceKey,
    {enablePeriodicRefetch: isActiveAgentInstanceStatus(agentInstanceStatus)},
  );

  const lastAgentStatus = useRef(agentInstanceStatus);
  useEffect(() => {
    // Trigger refetch to show up-to-date data faster once agent status changes are known.
    if (isEnabled && lastAgentStatus.current !== agentInstanceStatus) {
      refetch();
    }
    lastAgentStatus.current = agentInstanceStatus;
  }, [agentInstanceStatus, isEnabled, refetch]);

  if (status === 'pending') {
    return (
      <div data-testid="latest-agent-message-skeleton">
        <SkeletonText heading paragraph lineCount={3} />
      </div>
    );
  }

  if (status === 'error') {
    return <StatusHint>Failed to load latest agent message.</StatusHint>;
  }

  if (data === null || data.role !== 'ASSISTANT') {
    return <StatusHint>The agent has not produced a message yet.</StatusHint>;
  }

  return (
    <ConversationMessage
      actor={data.role}
      content={data.content}
      toolCalls={data.toolCalls}
      historyItemKey={data.historyItemKey}
    />
  );
};

export {LatestAgentMessage};
