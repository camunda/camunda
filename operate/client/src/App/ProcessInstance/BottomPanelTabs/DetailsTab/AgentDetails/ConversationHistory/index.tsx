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
import type {InfiniteData} from '@tanstack/react-query';
import {isActiveAgentInstanceStatus} from 'modules/queries/agentInstances/agentInstanceStatus';

function mapIntoLoopIterationChunks(
  pages: InfiniteData<QueryAgentInstanceHistoryResponseBody>,
): [number, AgentInstanceHistoryItem[]][] {
  const history = flattenPaginatedPages(pages).items;
  const loopIterationMap = new Map<number, AgentInstanceHistoryItem[]>();

  for (const item of history) {
    const iteration = item.loopIteration;
    if (iteration === null) {
      // The connector never set's null and actively guards against it.
      // Properly grouping those messages is non trivial. To avoid accidental
      // complexity, this theoretical case is dropped.
      continue;
    }
    const bucket = loopIterationMap.get(iteration) ?? [];
    bucket.push(item);
    loopIterationMap.set(iteration, bucket);
  }
  return Array.from(loopIterationMap);
}

type ConversationHistoryProps = {
  agentInstanceKey: string;
  agentInstanceStatus: AgentInstanceStatus;
  availableTools: AgentTool[];
  isVisible: boolean;
  selectedElementInstanceKey: string | null;
  agentsElementInstanceKeys: string[];
};

const ConversationHistory: React.FC<ConversationHistoryProps> = ({
  agentInstanceKey,
  agentInstanceStatus,
  availableTools,
  isVisible,
  selectedElementInstanceKey,
  agentsElementInstanceKeys,
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
        {data.length === 0 ? (
          <StatusHint>
            {canBeScoped && isScoped
              ? 'No scoped conversation with the agent instance found.'
              : 'No conversation with this agent instance found.'}
          </StatusHint>
        ) : (
          data.map(([iteration, items]) => {
            const loopIterationNode = (
              <LoopIterationMarker>
                {iteration}.&nbsp;loop&nbsp;iteration
              </LoopIterationMarker>
            );
            return (
              <Fragment key={iteration}>
                {sortOrder === 'asc' && loopIterationNode}
                {items.map((item) =>
                  item.role === 'TOOL_RESULT' ? (
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
