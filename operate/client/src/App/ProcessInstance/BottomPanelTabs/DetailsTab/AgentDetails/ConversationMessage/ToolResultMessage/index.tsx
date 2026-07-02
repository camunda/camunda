/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  AgentInstanceHistoryItem,
  AgentInstanceToolCall,
  AgentTool,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {Maximize, Tools} from '@carbon/react/icons';
import {
  Container,
  ResultPreview,
  ToolActions,
  ToolHeader,
  ToolLabel,
} from './styled';
import {Button} from '@carbon/react';

type ContentItem = AgentInstanceHistoryItem['content'][number];

const getResultPreview = (result: ContentItem[]): string => {
  const entry = result[0];
  if (!entry) {
    return 'Tool result has no content.';
  } else if (entry.contentType === 'TEXT') {
    return entry.text;
  } else if (entry.contentType === 'OBJECT') {
    return JSON.stringify(entry.object);
  } else {
    return 'Tool result has no viewable content.';
  }
};

type ToolResultMessageProps = {
  historyItemKey?: string;
  availableTools: AgentTool[];
  toolCalls: AgentInstanceToolCall[];
  result: ContentItem[];
};

const ToolResultMessage: React.FC<ToolResultMessageProps> = ({
  historyItemKey,
  toolCalls,
  result,
}) => {
  // According to the API, the tool call is a single entry in the tool calls list on a TOOL_RESULT message.
  const toolCall = toolCalls[0];
  if (!toolCall) {
    return null;
  }

  const resultPreview = getResultPreview(result);

  return (
    <Container
      $actor="TOOL_RESULT"
      data-testid={`conversation-message${historyItemKey ? `-${historyItemKey}` : ''}`}
      aria-label={`Result for "${toolCall.toolName}" tool call`}
    >
      <ToolHeader>
        <Tools size={12} />
        <ToolLabel>{toolCall.toolName}</ToolLabel>
      </ToolHeader>
      <ResultPreview>{resultPreview}</ResultPreview>
      <ToolActions>
        <Button
          disabled // disabled until implemented.
          kind="ghost"
          size="sm"
          renderIcon={Maximize}
          iconDescription="Expand"
          tooltipAlignment="end"
          hasIconOnly
        />
      </ToolActions>
    </Container>
  );
};

export {ToolResultMessage};
