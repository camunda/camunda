/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Button, SkeletonText} from '@carbon/react';
import {SortAscending, SortDescending} from '@carbon/react/icons';
import type {QuerySortOrder} from '@camunda/camunda-api-zod-schemas/8.10';
import {useAgentInstanceHistory} from 'modules/queries/agentInstances/useAgentInstanceHistory';
import {useProcessInstanceElementSelection} from 'modules/hooks/useProcessInstanceElementSelection';
import {ConversationMessage} from '../ConversationMessage';
import {ConversationContainer, ErrorHint, ShowMoreButton} from './styled';
import {flattenPaginatedPages} from 'modules/queries/flattenPaginatedPages';

type ConversationHistoryProps = {
  agentInstanceKey: string;
  isVisible: boolean;
  enablePeriodicRefetch: boolean;
};

const ConversationHistory: React.FC<ConversationHistoryProps> = ({
  agentInstanceKey,
  isVisible,
  enablePeriodicRefetch,
}) => {
  const [sortOrder, setSortOrder] = useState<QuerySortOrder>('desc');
  const {selectElement} = useProcessInstanceElementSelection();
  const {data, status, hasNextPage, fetchNextPage, isFetchingNextPage} =
    useAgentInstanceHistory(agentInstanceKey, {
      enabled: isVisible,
      enablePeriodicRefetch,
      sortOrder,
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
    return (
      <ErrorHint data-testid="conversation-history-error">
        Failed to load conversation history.
      </ErrorHint>
    );
  }

  return (
    <ConversationContainer>
      <Button
        kind="ghost"
        size="xs"
        renderIcon={sortOrder === 'desc' ? SortDescending : SortAscending}
        onClick={() =>
          setSortOrder((prev) => (prev === 'desc' ? 'asc' : 'desc'))
        }
      >
        {sortOrder === 'desc' ? 'Most recent first' : 'Oldest first'}
      </Button>
      {data.items.map((item) => (
        <ConversationMessage
          key={item.historyItemKey}
          historyItemKey={item.historyItemKey}
          actor={item.role}
          content={item.content}
          toolCalls={item.toolCalls}
          onToolCallClick={(toolCall) => {
            if (toolCall.elementId !== null) {
              selectElement({elementId: toolCall.elementId});
            }
          }}
        />
      ))}
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
