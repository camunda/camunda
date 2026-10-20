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
import {HistoryCardModal} from './HistoryCardModal';
import {getDocumentProcessing} from './documentProcessing';

const chronologicalCards = (data: Map<number, AgentInstanceHistoryItem[]>) =>
  Array.from(data.values())
    .flat()
    .filter(
      (item) =>
        item.role !== 'CONFIGURATION' &&
        (item.role !== 'TOOL_RESULT' || item.toolCalls.length > 0),
    )
    .sort(
      (a, b) =>
        Date.parse(a.producedAt) - Date.parse(b.producedAt) ||
        (BigInt(a.historyItemKey) < BigInt(b.historyItemKey) ? -1 : 1),
    );

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
  const [selection, setSelection] = useState<{
    agent: string;
    scope: string | null;
    key: string;
  } | null>(null);
  const messagesRef = useRef<HTMLDivElement>(null);
  const navigationVersion = useRef(0);
  const scope = canBeScoped && isScoped ? selectedElementInstanceKey : null;
  useEffect(() => {
    navigationVersion.current++;
  }, [agentInstanceKey, scope]);
  if (
    selection &&
    (selection.agent !== agentInstanceKey || selection.scope !== scope)
  ) {
    setSelection(null);
  }

  const {
    data,
    status,
    refetch,
    isEnabled,
    isPlaceholderData,
    hasNextPage,
    fetchNextPage,
    isFetchingNextPage,
    isFetchNextPageError,
  } = useAgentInstanceHistory(agentInstanceKey, {
    enabled: isVisible,
    enablePeriodicRefetch: isActiveAgentInstanceStatus(agentInstanceStatus),
    sortOrder,
    elementInstanceKey:
      canBeScoped && isScoped ? selectedElementInstanceKey : null,
    select: mapIntoLoopIterationChunks,
  });

  const cards = data ? chronologicalCards(data) : [];
  const documentProcessing = getDocumentProcessing(cards);
  const groupedSources = new Set(
    Array.from(documentProcessing.values(), (group) => group.sourceKey),
  );
  const selectedIndex =
    selection?.agent === agentInstanceKey && selection.scope === scope
      ? cards.findIndex((item) => item.historyItemKey === selection.key)
      : -1;
  const selectedCard = cards[selectedIndex];
  const openCard = (key: string) =>
    setSelection({agent: agentInstanceKey, scope, key});
  const navigate = async (direction: -1 | 1) => {
    if (!selectedCard) {
      return;
    }
    const version = navigationVersion.current;
    let loadedCards = cards;
    let index = selectedIndex;
    let more = hasNextPage;
    while (!loadedCards[index + direction] && more) {
      const result = await fetchNextPage();
      if (navigationVersion.current !== version) {
        return;
      }
      if (result.isError || !result.data) {
        return;
      }
      loadedCards = chronologicalCards(result.data);
      index = loadedCards.findIndex(
        (item) => item.historyItemKey === selectedCard.historyItemKey,
      );
      more = result.hasNextPage;
    }
    const neighbor = loadedCards[index + direction];
    if (neighbor) {
      openCard(neighbor.historyItemKey);
    }
  };
  const closeCard = () => {
    navigationVersion.current++;
    const key = selectedCard?.historyItemKey;
    setSelection(null);
    if (key) {
      requestAnimationFrame(() => {
        const card = messagesRef.current?.querySelector(
          `[data-history-card="${key}"]`,
        );
        card?.scrollIntoView?.({block: 'nearest'});
        card
          ?.querySelector<HTMLButtonElement>('[data-history-expand]')
          ?.focus();
      });
    }
  };

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

  if (status === 'pending') {
    return (
      <div data-testid="conversation-history-skeleton">
        <SkeletonText heading paragraph lineCount={3} />
      </div>
    );
  }

  if (status === 'error' && !data) {
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
      <Messages ref={messagesRef} data-dimmed={isPlaceholderData}>
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
                {items.map((item) => (
                  <Fragment key={item.historyItemKey}>
                    {item.role === 'CONFIGURATION' ? null : item.role ===
                      'TOOL_RESULT' ? (
                      <div
                        key={item.historyItemKey}
                        data-history-card={item.historyItemKey}
                      >
                        <ToolResultMessage
                          availableTools={availableTools}
                          toolCalls={item.toolCalls}
                          content={item.content}
                          parsingInput={
                            documentProcessing.get(item.historyItemKey)?.input
                          }
                          onExpand={() => openCard(item.historyItemKey)}
                        />
                      </div>
                    ) : (
                      <div
                        key={item.historyItemKey}
                        data-history-card={item.historyItemKey}
                      >
                        <ConversationMessage
                          historyItemKey={item.historyItemKey}
                          actor={item.role}
                          content={item.content}
                          toolCalls={item.toolCalls}
                          metrics={item.metrics}
                          onExpand={() => openCard(item.historyItemKey)}
                          label={
                            item.role === 'USER' &&
                            firstUserMessageKey !== undefined &&
                            item.historyItemKey !== firstUserMessageKey
                              ? 'User · analyst feedback (revise)'
                              : undefined
                          }
                          hideDocumentParsing={groupedSources.has(
                            item.historyItemKey,
                          )}
                        />
                      </div>
                    )}
                  </Fragment>
                ))}
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
      {selectedCard && !isPlaceholderData && (
        <HistoryCardModal
          item={selectedCard}
          parsingInput={
            documentProcessing.get(selectedCard.historyItemKey)?.input
          }
          hideDocumentParsing={groupedSources.has(selectedCard.historyItemKey)}
          availableTools={availableTools}
          position={selectedIndex + 1}
          total={cards.length}
          hasEarlier={
            selectedIndex > 0 || (hasNextPage && sortOrder === 'desc')
          }
          hasLater={
            selectedIndex < cards.length - 1 ||
            (hasNextPage && sortOrder === 'asc')
          }
          loading={isFetchingNextPage}
          loadError={isFetchNextPageError}
          moreUnloaded={hasNextPage}
          onPrevious={() => navigate(-1)}
          onNext={() => navigate(1)}
          onClose={closeCard}
        />
      )}
    </ConversationContainer>
  );
};

export {ConversationHistory};
