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
import {AccordionItem} from '@carbon/react';
import {
  CircleDash,
  WarningFilled,
  CheckmarkOutline,
  Time,
  MeterAlt,
} from '@carbon/react/icons';
import {
  AgentDetailsContainer,
  AgentHeading,
  Accordion,
  StatusRow,
  StatusIconWrapper,
  StatusLabel,
  MetricsRow,
} from './styled';
import {ModelCallsMetric} from './AgentMetrics/ModelCallsMetric';
import {TokensUsedMetric} from './AgentMetrics/TokensUsedMetric';
import {ToolsCalledMetric} from './AgentMetrics/ToolsCalledMetric';
import {SectionTitle} from './SectionTitle';

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
  const {metrics, limits} = agentInstance;

  return (
    <AgentDetailsContainer data-testid="agent-details">
      <AgentHeading>AI Agent</AgentHeading>
      <StatusRow data-testid="agent-status-row">
        <StatusIconWrapper>
          <StatusIcon status={agentInstance.status} />
        </StatusIconWrapper>
        <StatusLabel>Status: {statusLabel}</StatusLabel>
      </StatusRow>
      <Accordion align="start">
        <AccordionItem
          data-testid="agent-usage-section"
          title={
            <SectionTitle icon={<MeterAlt size={16} />}>Usage</SectionTitle>
          }
        >
          <MetricsRow>
            <ModelCallsMetric
              modelCalls={metrics.modelCalls}
              maxModelCalls={limits.maxModelCalls}
            />
            <TokensUsedMetric
              inputTokens={metrics.inputTokens}
              outputTokens={metrics.outputTokens}
            />
            <ToolsCalledMetric toolCalls={metrics.toolCalls} />
          </MetricsRow>
        </AccordionItem>
      </Accordion>
    </AgentDetailsContainer>
  );
};

export {AgentDetails};
