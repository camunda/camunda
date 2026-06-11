/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {SkeletonText} from '@carbon/react';
import {useAgentInstanceHistory} from 'modules/queries/agentInstances/useAgentInstanceHistory';
import {ConversationMessage} from '../ConversationMessage';
import {ConversationContainer, ErrorHint} from './styled';

type ConversationHistoryProps = {
  agentInstanceKey: string;
  enablePeriodicRefetch: boolean;
};

const ConversationHistory: React.FC<ConversationHistoryProps> = ({
  agentInstanceKey,
  enablePeriodicRefetch,
}) => {
  const {data, status} = useAgentInstanceHistory(agentInstanceKey, {
    enablePeriodicRefetch,
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
      {data.items.map((item) => (
        <ConversationMessage
          key={item.historyItemKey}
          historyItemKey={item.historyItemKey}
          actor={item.role}
          content={item.content}
        />
      ))}
    </ConversationContainer>
  );
};

export {ConversationHistory};
