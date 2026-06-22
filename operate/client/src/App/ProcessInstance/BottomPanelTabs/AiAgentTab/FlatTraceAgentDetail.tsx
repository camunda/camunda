/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo} from 'react';
import {Accordion, AccordionItem, Tag} from '@carbon/react';
import {MeterAlt, Chat, Tools, DocumentBlank, Chip} from '@carbon/icons-react';
import {useQueries} from '@tanstack/react-query';
import type {AgentElementData} from 'modules/contexts/agentData.types';
import {useAgentData} from 'modules/contexts/agentData';
import {TOOL_DESCRIPTIONS} from 'modules/queries/agentInstances/historyToAgentElementData';
import {useSearchElementInstancesByScope} from 'modules/queries/elementInstances/useSearchElementInstancesByScope';
import {searchElementInstances} from 'modules/api/v2/elementInstances/searchElementInstances';
import {AgentAccordionContainer, MetaLabel} from './styled';
import {
  TokensStatCard,
  ToolsCalledStatCard,
  ModelCallsStatCard,
  ExpandableMessageBlock,
  StatusAccordion,
} from './AgentDetailPanel';
import {FlatTraceConversation} from './FlatTraceConversation';
import type {ToolInstanceLink} from './FlatTraceConversation';

const accordionTitle = (
  Icon: React.ComponentType<{size?: number}>,
  label: string,
  chip?: React.ReactNode,
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
    {chip}
  </span>
);

function FlatTraceAgentDetail({agentData}: {agentData: AgentElementData}) {
  const {primaryAgentElementInstanceKey} = useAgentData();

  // Step 1: fetch all AD_HOC_SUB_PROCESS_INNER_INSTANCE children of the outer
  // subprocess. Each inner instance wraps exactly one tool activation.
  const {data: innerInstancesResult} = useSearchElementInstancesByScope(
    {
      filter: {
        elementInstanceScopeKey: primaryAgentElementInstanceKey ?? '',
      },
      page: {limit: 200},
    },
    {enabled: !!primaryAgentElementInstanceKey},
  );

  const innerInstances = useMemo(
    () =>
      (innerInstancesResult?.items ?? []).filter(
        (el) => el.type === 'AD_HOC_SUB_PROCESS_INNER_INSTANCE',
      ),
    [innerInstancesResult],
  );

  // Step 2: for each inner instance, fetch its tool children (one per inner
  // instance). We use useQueries to fan out in parallel.
  const toolChildResults = useQueries({
    queries: innerInstances.map((inner) => ({
      queryKey: [
        'elementInstancesSearchByScope',
        {filter: {elementInstanceScopeKey: inner.elementInstanceKey}},
      ],
      queryFn: async () => {
        const {response, error} = await searchElementInstances({
          filter: {elementInstanceScopeKey: inner.elementInstanceKey},
          page: {limit: 10},
        });
        if (response !== null) {
          return {innerInstance: inner, items: response.items};
        }
        throw error;
      },
    })),
  });

  // Build map: toolElementId → ToolInstanceLink
  const toolInstanceMap = useMemo(() => {
    const map = new Map<string, ToolInstanceLink>();
    for (const result of toolChildResults) {
      if (!result.data) continue;
      const {innerInstance, items} = result.data;
      for (const child of items) {
        map.set(child.elementId, {
          innerInstanceKey: innerInstance.elementInstanceKey,
          innerElementId: innerInstance.elementId,
          anchorElementId: child.elementId,
          displayName: child.elementName ?? child.elementId,
        });
      }
    }
    return map;
  }, [toolChildResults]);

  return (
    <AgentAccordionContainer>
      <Accordion align="start">
        <StatusAccordion agentData={agentData} />
        <AccordionItem
          title={accordionTitle(
            MeterAlt,
            'Usage',
            <>
              <Tag type="gray" size="sm">
                {agentData.usage.modelCalls.current} model calls
              </Tag>
              <Tag type="gray" size="sm">
                {agentData.usage.tokensUsed.totalTokens.toLocaleString()} tokens
              </Tag>
            </>,
          )}
        >
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
              <FlatTraceConversation
                agentData={agentData}
                toolInstanceMap={toolInstanceMap}
              />
            </div>
          </AccordionItem>
        )}

        <AccordionItem title={accordionTitle(DocumentBlank, 'System prompt')}>
          <div style={{width: '100%'}}>
            <ExpandableMessageBlock
              role="System"
              borderColor="var(--cds-border-subtle-01)"
              contents={[agentData.systemPrompt]}
            />
          </div>
        </AccordionItem>

        <AccordionItem title={accordionTitle(Tools, 'Tool descriptions')}>
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
