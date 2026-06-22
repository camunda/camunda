/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Accordion, AccordionItem} from '@carbon/react';
import {MeterAlt, Chat, Tools, DocumentBlank, Chip} from '@carbon/icons-react';
import type {AgentElementData} from 'modules/contexts/agentData.types';
import {TOOL_DESCRIPTIONS} from 'modules/queries/agentInstances/historyToAgentElementData';
import {AgentAccordionContainer, MetaLabel} from './styled';
import {
  TokensStatCard,
  ToolsCalledStatCard,
  ModelCallsStatCard,
  ExpandableMessageBlock,
  StatusAccordion,
} from './AgentDetailPanel';
import {FlatTraceConversation} from './FlatTraceConversation';

const accordionTitle = (
  Icon: React.ComponentType<{size?: number}>,
  label: string,
) => (
  <span
    style={{
      display: 'inline-flex',
      alignItems: 'center',
      gap: 'var(--cds-spacing-03)',
    }}
  >
    <Icon size={16} />
    {label}
  </span>
);

function FlatTraceAgentDetail({agentData}: {agentData: AgentElementData}) {
  return (
    <AgentAccordionContainer>
      <Accordion align="start">
        <StatusAccordion agentData={agentData} />
        <AccordionItem title={accordionTitle(MeterAlt, 'Usage')} open>
          <div
            style={{
              display: 'flex',
              gap: 'var(--cds-spacing-05)',
              alignItems: 'stretch',
              width: '100%',
            }}
          >
            <div style={{flex: 1, display: 'flex'}}>
              <ModelCallsStatCard
                current={agentData.usage.modelCalls.current}
                limit={agentData.usage.modelCalls.limit}
              />
            </div>
            <div style={{flex: 1, display: 'flex'}}>
              <TokensStatCard usage={agentData.usage.tokensUsed} />
            </div>
            <div style={{flex: 1, display: 'flex'}}>
              <ToolsCalledStatCard
                current={agentData.usage.toolsCalled.current}
              />
            </div>
          </div>
        </AccordionItem>

        {agentData.conversation && agentData.conversation.length > 0 && (
          <AccordionItem
            title={accordionTitle(Chat, 'Conversation history')}
            open
          >
            <div style={{width: '100%'}}>
              <FlatTraceConversation agentData={agentData} />
            </div>
          </AccordionItem>
        )}

        <AccordionItem title={accordionTitle(Tools, 'Tool definitions')}>
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 'var(--cds-spacing-05)',
              width: '100%',
            }}
          >
            {agentData.toolDefinitions.map((tool) => (
              <div key={tool.name}>
                <MetaLabel>{tool.name}</MetaLabel>
                <div
                  style={{
                    fontSize: 'var(--cds-body-compact-01-font-size)',
                    lineHeight: '1.5',
                    color: 'var(--cds-text-secondary)',
                  }}
                >
                  {tool.description ||
                    TOOL_DESCRIPTIONS[tool.name] ||
                    'No description available.'}
                </div>
              </div>
            ))}
          </div>
        </AccordionItem>

        <AccordionItem title={accordionTitle(DocumentBlank, 'System prompt')}>
          <div style={{width: '100%'}}>
            <ExpandableMessageBlock
              role="System"
              borderColor="var(--cds-border-subtle-01)"
              contents={[agentData.systemPrompt]}
            />
          </div>
        </AccordionItem>

        <AccordionItem title={accordionTitle(Chip, 'Model')}>
          <div
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: 'var(--cds-spacing-02)',
              fontSize: 'var(--cds-body-compact-01-font-size)',
              lineHeight: '1.5',
              color: 'var(--cds-text-secondary)',
              width: '100%',
            }}
          >
            <div>
              <strong style={{color: 'var(--cds-text-primary)'}}>
                Provider:
              </strong>{' '}
              {agentData.modelProvider}
            </div>
            <div>
              <strong style={{color: 'var(--cds-text-primary)'}}>Model:</strong>{' '}
              {agentData.modelId}
            </div>
          </div>
        </AccordionItem>
      </Accordion>
    </AgentAccordionContainer>
  );
}

export {FlatTraceAgentDetail};
