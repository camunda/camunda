/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  AgentInstance,
  AgentInstanceStatus,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {
  CircleDash,
  WarningFilled,
  CheckmarkOutline,
  Time,
} from '@carbon/react/icons';
import {
  AgentDetailsContainer,
  AgentHeading,
  StatusRow,
  StatusIconWrapper,
  StatusLabel,
} from './styled';

const STATUS_LABELS: Record<AgentInstanceStatus, string> = {
  INITIALIZING: 'Initializing',
  THINKING: 'Thinking',
  TOOL_CALLING: 'Calling tools',
  TOOL_DISCOVERY: 'Discovering tools',
  IDLE: 'Idle',
  COMPLETED: 'Completed',
};

function StatusIcon({status}: {status: AgentInstanceStatus}) {
  switch (status) {
    case 'INITIALIZING':
    case 'THINKING':
    case 'TOOL_CALLING':
    case 'TOOL_DISCOVERY':
      return <Time size={16} />;
    case 'IDLE':
      return <CircleDash size={16} />;
    case 'COMPLETED':
      return <CheckmarkOutline size={16} />;
    default:
      return <WarningFilled size={16} />;
  }
}

type AgentDetailsProps = {
  agentInstance: AgentInstance | undefined;
  isLoading: boolean;
  isError: boolean;
};

const AgentDetails: React.FC<AgentDetailsProps> = ({
  agentInstance,
  isLoading,
  isError,
}) => {
  if (isLoading) {
    return (
      <AgentDetailsContainer>
        <AgentHeading>AI Agent</AgentHeading>
        <StatusRow>
          <StatusLabel>Loading...</StatusLabel>
        </StatusRow>
      </AgentDetailsContainer>
    );
  }

  if (isError || !agentInstance) {
    return (
      <AgentDetailsContainer>
        <AgentHeading>AI Agent</AgentHeading>
        <StatusRow>
          <StatusIconWrapper>
            <WarningFilled size={16} />
          </StatusIconWrapper>
          <StatusLabel>Unable to load agent status</StatusLabel>
        </StatusRow>
      </AgentDetailsContainer>
    );
  }

  const statusLabel =
    STATUS_LABELS[agentInstance.status] ?? agentInstance.status;

  return (
    <AgentDetailsContainer data-testid="agent-details">
      <AgentHeading>AI Agent</AgentHeading>
      <StatusRow data-testid="agent-status-row">
        <StatusIconWrapper>
          <StatusIcon status={agentInstance.status} />
        </StatusIconWrapper>
        <StatusLabel>Status: {statusLabel}</StatusLabel>
      </StatusRow>
    </AgentDetailsContainer>
  );
};

export {AgentDetails};
