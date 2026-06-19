/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {SkeletonText} from '@carbon/react';
import {useLatestAgentMessage} from 'modules/queries/agentInstances/useLatestAgentMessage';
import {useProcessInstanceElementSelectActions} from 'modules/hooks/useProcessInstanceElementSelection';
import {ConversationMessage} from '../ConversationMessage';
import {StatusHint} from './styled';

type LatestAgentMessageProps = {
  agentInstanceKey: string;
  enablePeriodicRefetch: boolean;
};

const LatestAgentMessage: React.FC<LatestAgentMessageProps> = ({
  agentInstanceKey,
  enablePeriodicRefetch,
}) => {
  const {selectElement} = useProcessInstanceElementSelectActions();
  const {data, status} = useLatestAgentMessage(agentInstanceKey, {
    enablePeriodicRefetch,
  });

  if (status === 'pending') {
    return (
      <div data-testid="latest-agent-message-skeleton">
        <SkeletonText heading paragraph lineCount={3} />
      </div>
    );
  }

  if (status === 'error') {
    return <StatusHint>Failed to load latest agent message.</StatusHint>;
  }

  if (data === null) {
    return <StatusHint>The agent has not produced a message yet.</StatusHint>;
  }

  return (
    <ConversationMessage
      actor={data.role}
      content={data.content}
      toolCalls={data.toolCalls}
      historyItemKey={data.historyItemKey}
      onToolCallClick={(toolCall) => {
        if (toolCall.elementId !== null) {
          selectElement({elementId: toolCall.elementId});
        }
      }}
    />
  );
};

export {LatestAgentMessage};
