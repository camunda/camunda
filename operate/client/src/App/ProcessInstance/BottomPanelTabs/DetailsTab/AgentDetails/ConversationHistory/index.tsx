/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Button, ButtonSet, SkeletonText} from '@carbon/react';
import {
  Filter,
  FilterRemove,
  SortAscending,
  SortDescending,
} from '@carbon/react/icons';
import type {QuerySortOrder} from '@camunda/camunda-api-zod-schemas/8.10';
import {useAgentInstanceHistory} from 'modules/queries/agentInstances/useAgentInstanceHistory';
import {useProcessInstanceElementSelectActions} from 'modules/hooks/useProcessInstanceElementSelection';
import {ConversationMessage} from '../ConversationMessage';
import {
  ConversationContainer,
  Messages,
  StatusHint,
  ShowMoreButton,
} from './styled';
import {flattenPaginatedPages} from 'modules/queries/flattenPaginatedPages';

type ConversationHistoryProps = {
  agentInstanceKey: string;
  isVisible: boolean;
  enablePeriodicRefetch: boolean;
  selectedElementInstanceKey: string | null;
  agentsElementInstanceKeys: string[];
};

const ConversationHistory: React.FC<ConversationHistoryProps> = ({
  agentInstanceKey,
  isVisible,
  enablePeriodicRefetch,
  selectedElementInstanceKey,
  agentsElementInstanceKeys,
}) => {
  const canBeScoped =
    agentsElementInstanceKeys.length > 1 && selectedElementInstanceKey !== null;

  const [sortOrder, setSortOrder] = useState<QuerySortOrder>('desc');
  const [isScoped, setIsScoped] = useState(true);

  const {selectElement} = useProcessInstanceElementSelectActions();
  const {
    data,
    status,
    isPlaceholderData,
    hasNextPage,
    fetchNextPage,
    isFetchingNextPage,
  } = useAgentInstanceHistory(agentInstanceKey, {
    enabled: isVisible,
    enablePeriodicRefetch,
    sortOrder,
    elementInstanceKey:
      canBeScoped && isScoped ? selectedElementInstanceKey : null,
    select: flattenPaginatedPages,
  });

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
        {data.items.length === 0 ? (
          <StatusHint>
            {canBeScoped && isScoped
              ? 'No scoped conversation with the agent instance found.'
              : 'No conversation with this agent instance found.'}
          </StatusHint>
        ) : (
          data.items.map((item) => (
            <ConversationMessage
              key={item.historyItemKey}
              historyItemKey={item.historyItemKey}
              actor={item.role}
              content={item.content}
              toolCalls={item.toolCalls}
              metrics={item.metrics}
              onToolCallClick={(toolCall) => {
                if (toolCall.elementId !== null) {
                  selectElement({elementId: toolCall.elementId});
                }
              }}
            />
          ))
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

type ConversationTogglesProps = {
  sortOrder: QuerySortOrder;
  canBeScoped: boolean;
  isScoped: boolean;
  onToggleSortOrder: () => void;
  onToggleScope: () => void;
};

const ConversationToggles: React.FC<ConversationTogglesProps> = (props) => (
  <ButtonSet>
    <Button
      kind="ghost"
      size="xs"
      renderIcon={props.sortOrder === 'desc' ? SortDescending : SortAscending}
      onClick={props.onToggleSortOrder}
    >
      {props.sortOrder === 'desc' ? 'Most recent first' : 'Oldest first'}
    </Button>
    {props.canBeScoped && (
      <Button
        kind="ghost"
        size="xs"
        renderIcon={props.isScoped ? Filter : FilterRemove}
        onClick={props.onToggleScope}
      >
        {props.isScoped ? 'Scoped conversation' : 'Whole conversation'}
      </Button>
    )}
  </ButtonSet>
);

export {ConversationHistory};
