/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Fragment, useEffect, useRef, useState} from 'react';
import {SkeletonText} from '@carbon/react';
import type {
  AgentInstanceHistoryItem,
  AgentInstanceStatus,
  AgentTool,
  QueryAgentInstanceHistoryResponseBody,
  QuerySortOrder,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {useAgentInstanceHistory} from 'modules/queries/agentInstances/useAgentInstanceHistory';
import {ConversationMessage} from '../ConversationMessage';
import {ConversationToggles} from './ConversationToggles';
import {
  ConversationContainer,
  Messages,
  StatusHint,
  ShowMoreButton,
  LoopIterationMarker,
} from './styled';
import {flattenPaginatedPages} from 'modules/queries/flattenPaginatedPages';
import {ToolResultMessage} from '../ConversationMessage/ToolResultMessage';
import {getContentEvidence} from '../ConversationMessage/EvidenceCard/evidence';
import type {InfiniteData} from '@tanstack/react-query';
import {isActiveAgentInstanceStatus} from 'modules/queries/agentInstances/agentInstanceStatus';

function mapIntoLoopIterationChunks(
  pages: InfiniteData<QueryAgentInstanceHistoryResponseBody>,
): Map<number, AgentInstanceHistoryItem[]> {
  const history = flattenPaginatedPages(pages).items;
  const loopIterationMap = new Map<number, AgentInstanceHistoryItem[]>();

  for (const item of history) {
    const bucket = loopIterationMap.get(item.loopIteration) ?? [];
    bucket.push(item);
    loopIterationMap.set(item.loopIteration, bucket);
  }
  return loopIterationMap;
}

type ConversationHistoryProps = {
  agentInstanceKey: string;
  agentInstanceStatus: AgentInstanceStatus;
  availableTools: AgentTool[];
  isVisible: boolean;
  selectedElementInstanceKey: string | null;
  agentsElementInstanceKeys: string[];
  credentialBinding?: string | null;
};

const ConversationHistory: React.FC<ConversationHistoryProps> = ({
  agentInstanceKey,
  agentInstanceStatus,
  availableTools,
  isVisible,
  selectedElementInstanceKey,
  agentsElementInstanceKeys,
  credentialBinding = null,
}) => {
  const canBeScoped =
    agentsElementInstanceKeys.length > 1 && selectedElementInstanceKey !== null;

  const [sortOrder, setSortOrder] = useState<QuerySortOrder>('desc');
  const [isScoped, setIsScoped] = useState(true);

  const {
    data,
    status,
    refetch,
    isEnabled,
    isPlaceholderData,
    hasNextPage,
    fetchNextPage,
    isFetchingNextPage,
  } = useAgentInstanceHistory(agentInstanceKey, {
    enabled: isVisible,
    enablePeriodicRefetch: isActiveAgentInstanceStatus(agentInstanceStatus),
    sortOrder,
    elementInstanceKey:
      canBeScoped && isScoped ? selectedElementInstanceKey : null,
    select: mapIntoLoopIterationChunks,
  });

  const lastAgentStatus = useRef(agentInstanceStatus);
  useEffect(() => {
    // Trigger refetch to show up-to-date data faster once agent status changes are known.
    if (isEnabled && lastAgentStatus.current !== agentInstanceStatus) {
      refetch();
    }
    lastAgentStatus.current = agentInstanceStatus;
  }, [agentInstanceStatus, isEnabled, refetch]);

  // The first user message starts the agent; later ones are analyst feedback
  // that sends the agent into another revise loop.
  const firstUserMessageKey =
    status === 'success'
      ? Array.from(data.entries())
          .sort(([a], [b]) => a - b)
          .flatMap(([, items]) => items)
          .find((item) => item.role === 'USER')?.historyItemKey
      : undefined;

  // The connector records the configuration on creation and on each turn
  // start; only show it when it changed.
  const visibleConfigurationKeys = new Set<string>();
  if (status === 'success') {
    let previous: string | null = null;
    for (const item of Array.from(data.entries())
      .sort(([a], [b]) => a - b)
      .flatMap(([, items]) => items)
      .filter((item) => item.role === 'CONFIGURATION')
      .sort((a, b) =>
        BigInt(a.historyItemKey) < BigInt(b.historyItemKey) ? -1 : 1,
      )) {
      const current = JSON.stringify(item.content);
      if (current !== previous && getContentEvidence(item.content).length > 0) {
        visibleConfigurationKeys.add(item.historyItemKey);
      }
      previous = current;
    }
  }

  if (status === 'pending') {
    return (
      <div data-testid="conversation-history-skeleton">
        <SkeletonText heading paragraph lineCount={3} />
      </div>
    );
  }

  if (status === 'error') {
    return <StatusHint>Failed to load conversation history.</StatusHint>;
  }

  return (
    <ConversationContainer>
      <ConversationToggles
        sortOrder={sortOrder}
        canBeScoped={canBeScoped}
        isScoped={isScoped}
        onToggleSortOrder={() =>
          setSortOrder((prev) => (prev === 'desc' ? 'asc' : 'desc'))
        }
        onToggleScope={() => setIsScoped((prev) => !prev)}
      />
      <Messages data-dimmed={isPlaceholderData}>
        {data.size === 0 ? (
          <StatusHint>
            {canBeScoped && isScoped
              ? 'No scoped conversation with the agent instance found.'
              : 'No conversation with this agent instance found.'}
          </StatusHint>
        ) : (
          Array.from(data, ([iteration, items]) => {
            const loopIterationNode = (
              <LoopIterationMarker>
                {iteration}.&nbsp;iteration
              </LoopIterationMarker>
            );
            return (
              <Fragment key={iteration}>
                {sortOrder === 'asc' && loopIterationNode}
                {items.map((item) =>
                  item.role === 'CONFIGURATION' ? (
                    visibleConfigurationKeys.has(item.historyItemKey) ? (
                      <ConversationMessage
                        key={item.historyItemKey}
                        historyItemKey={item.historyItemKey}
                        actor="SYSTEM"
                        label="Agent configuration"
                        content={item.content}
                        credentialBinding={credentialBinding}
                      />
                    ) : null
                  ) : item.role === 'TOOL_RESULT' ? (
                    <ToolResultMessage
                      key={item.historyItemKey}
                      availableTools={availableTools}
                      toolCalls={item.toolCalls}
                      content={item.content}
                    />
                  ) : (
                    <ConversationMessage
                      key={item.historyItemKey}
                      historyItemKey={item.historyItemKey}
                      actor={item.role}
                      content={item.content}
                      toolCalls={item.toolCalls}
                      metrics={item.metrics}
                      label={
                        item.role === 'USER' &&
                        firstUserMessageKey !== undefined &&
                        item.historyItemKey !== firstUserMessageKey
                          ? 'User · analyst feedback (revise)'
                          : undefined
                      }
                    />
                  ),
                )}
                {sortOrder === 'desc' && loopIterationNode}
              </Fragment>
            );
          })
        )}
      </Messages>
      {isFetchingNextPage && <SkeletonText paragraph lineCount={2} />}
      {!isFetchingNextPage && hasNextPage && (
        <ShowMoreButton kind="ghost" size="sm" onClick={() => fetchNextPage()}>
          Show more
        </ShowMoreButton>
      )}
    </ConversationContainer>
  );
};

export {ConversationHistory};
