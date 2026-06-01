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
  Chip,
  DocumentBlank,
} from '@carbon/react/icons';
import {
  AgentDetailsContainer,
  AgentHeading,
  Accordion,
  LoadingStatusHint,
  MetricsRow,
  ModelInfo,
  ModelInfoLabel,
} from './styled';
import {ModelCallsMetric} from './AgentMetrics/ModelCallsMetric';
import {TokensUsedMetric} from './AgentMetrics/TokensUsedMetric';
import {ToolsCalledMetric} from './AgentMetrics/ToolsCalledMetric';
import {SectionTitle} from './SectionTitle';
import {ConversationMessage} from './ConversationMessage';

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
        <LoadingStatusHint>Loading...</LoadingStatusHint>
      </AgentDetailsContainer>
    );
  }

  if (isError || !agentInstance) {
    return (
      <AgentDetailsContainer>
        <AgentHeading>AI Agent</AgentHeading>
        <LoadingStatusHint>Unable to load agent information.</LoadingStatusHint>
      </AgentDetailsContainer>
    );
  }

  const statusLabel =
    STATUS_LABELS[agentInstance.status] ?? agentInstance.status;
  const {metrics, limits, definition} = agentInstance;

  return (
    <AgentDetailsContainer data-testid="agent-details">
      <AgentHeading>AI Agent</AgentHeading>
      <Accordion align="start">
        <AccordionItem
          data-testid="agent-status-section"
          disabled
          title={
            <SectionTitle icon={<StatusIcon status={agentInstance.status} />}>
              Status: {statusLabel}
            </SectionTitle>
          }
        ></AccordionItem>
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
              maxTokens={limits.maxTokens}
            />
            <ToolsCalledMetric
              toolCalls={metrics.toolCalls}
              maxToolCalls={limits.maxToolCalls}
            />
          </MetricsRow>
        </AccordionItem>
        <AccordionItem
          data-testid="agent-system-prompt-section"
          title={
            <SectionTitle icon={<DocumentBlank size={16} />}>
              System prompt
            </SectionTitle>
          }
        >
          <ConversationMessage
            actor="system"
            messages={[definition.systemPrompt]}
          />
        </AccordionItem>
        <AccordionItem
          data-testid="agent-model-section"
          title={<SectionTitle icon={<Chip size={16} />}>Model</SectionTitle>}
        >
          <ModelInfo>
            <ModelInfoLabel>Provider:</ModelInfoLabel> {definition.provider}
          </ModelInfo>
          <ModelInfo>
            <ModelInfoLabel>Model:</ModelInfoLabel> {definition.model}
          </ModelInfo>
        </AccordionItem>
      </Accordion>
    </AgentDetailsContainer>
  );
};

export {AgentDetails};
