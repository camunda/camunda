/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import type {
  AgentInstance,
  AgentInstanceStatus,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {Accordion, AccordionItem, AccordionSkeleton} from '@carbon/react';
import {
  CircleDash,
  WarningFilled,
  CheckmarkOutline,
  Time,
  MeterAlt,
  Chip,
  DocumentBlank,
  Chat,
  Tools,
} from '@carbon/react/icons';
import {
  AgentDetailsContainer,
  AgentHeading,
  ErrorHint,
  MetricsRow,
  ModelInfo,
  ModelInfoLabel,
} from './styled';
import {ModelCallsMetric} from './AgentMetrics/ModelCallsMetric';
import {TokensUsedMetric} from './AgentMetrics/TokensUsedMetric';
import {ToolsCalledMetric} from './AgentMetrics/ToolsCalledMetric';
import {SectionTitle} from './SectionTitle';
import {ConversationMessage} from './ConversationMessage';
import {ConversationHistory} from './ConversationHistory';
import {LatestAgentMessage} from './ConversationHistory/LatestAgentMessage';
import {AvailableTools} from './AvailableTools';
import {isAgentInstanceActive} from 'modules/queries/agentInstances/agentInstanceStatus';

const STATUS_LABELS: Record<AgentInstanceStatus, string> = {
  UNKNOWN: 'Unknown',
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
  const [isConversationHistoryOpen, setIsConversationHistoryOpen] =
    useState(false);

  if (isLoading) {
    return (
      <AgentDetailsContainer>
        <AgentHeading>AI Agent</AgentHeading>
        <AccordionSkeleton
          align="start"
          count={4}
          open={false}
          data-testid="agent-details-skeleton"
        />
      </AgentDetailsContainer>
    );
  }

  if (isError || !agentInstance) {
    return (
      <AgentDetailsContainer>
        <AgentHeading>AI Agent</AgentHeading>
        <ErrorHint>Unable to load agent information.</ErrorHint>
      </AgentDetailsContainer>
    );
  }

  const statusLabel =
    STATUS_LABELS[agentInstance.status] ?? agentInstance.status;
  const {metrics, limits, definition} = agentInstance;

  return (
    <AgentDetailsContainer
      data-testid="agent-details"
      onKeyDown={(e) => {
        // TODO: Workaround for https://github.com/carbon-design-system/carbon/issues/22483.
        if (
          e.key === 'Escape' &&
          (e.target as HTMLElement).innerText === 'Conversation history'
        ) {
          setIsConversationHistoryOpen(false);
        }
      }}
    >
      <AgentHeading>AI Agent</AgentHeading>
      <Accordion align="start">
        <AccordionItem
          data-testid="agent-status-section"
          open
          title={
            <SectionTitle icon={<StatusIcon status={agentInstance.status} />}>
              Status: {statusLabel}
            </SectionTitle>
          }
        >
          <LatestAgentMessage
            agentInstanceKey={agentInstance.agentInstanceKey}
            enablePeriodicRefetch={isAgentInstanceActive(agentInstance)}
          />
        </AccordionItem>
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
          data-testid="agent-conversation-history-section"
          open={isConversationHistoryOpen}
          onHeadingClick={({isOpen}) => setIsConversationHistoryOpen(isOpen)}
          title={
            <SectionTitle icon={<Chat size={16} />}>
              Conversation history
            </SectionTitle>
          }
        >
          <ConversationHistory
            agentInstanceKey={agentInstance.agentInstanceKey}
            isVisible={isConversationHistoryOpen}
            enablePeriodicRefetch={isAgentInstanceActive(agentInstance)}
          />
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
            actor="SYSTEM"
            content={[{contentType: 'TEXT', text: definition.systemPrompt}]}
          />
        </AccordionItem>
        <AccordionItem
          data-testid="agent-available-tools-section"
          title={
            <SectionTitle icon={<Tools size={16} />}>
              Available tools
            </SectionTitle>
          }
        >
          <AvailableTools tools={agentInstance.tools} />
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
