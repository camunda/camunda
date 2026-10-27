/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useMemo, useState} from 'react';
import type {
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
import {getResultPreview, type ContentItem} from './getRenderableResult';
import {ToolResultModal} from './ToolResultModal';
import {DocumentContent} from '../MessageAttachments/DocumentContent';

type ToolResultMessageProps = {
  availableTools: AgentTool[];
  toolCalls: AgentInstanceToolCall[];
  content: ContentItem[];
};

const ToolResultMessage: React.FC<ToolResultMessageProps> = ({
  availableTools,
  toolCalls,
  content,
}) => {
  // According to the API, the tool call is a single entry in the tool calls list on a TOOL_RESULT message.
  const toolCall = toolCalls[0];

  const [isExpanded, setIsExpanded] = useState(false);

  const resultPreview = useMemo(() => getResultPreview(content), [content]);
  const description = useMemo(() => {
    const tool = availableTools.find(
      (tool) => tool.name === toolCall?.toolName,
    );
    return tool?.description ?? null;
  }, [availableTools, toolCall]);

  if (!toolCall) {
    return null;
  }

  return (
    <Container
      $actor="TOOL_RESULT"
      data-testid={`tool-call-result-${toolCall.toolCallId}`}
      aria-label={`Result for "${toolCall.toolName}" tool call`}
    >
      <ToolHeader>
        <Tools size={12} />
        <ToolLabel>{toolCall.toolName}</ToolLabel>
      </ToolHeader>
      {resultPreview !== null && <ResultPreview>{resultPreview}</ResultPreview>}
      <DocumentContent content={content} modalTitleSuffix="tool result" />
      <ToolActions>
        <Button
          kind="ghost"
          size="sm"
          renderIcon={Maximize}
          iconDescription="Expand"
          tooltipAlignment="end"
          hasIconOnly
          onClick={() => setIsExpanded(true)}
        />
      </ToolActions>
      {isExpanded && (
        <ToolResultModal
          toolName={toolCall.toolName}
          description={description}
          input={toolCall.arguments}
          content={content}
          onClose={() => setIsExpanded(false)}
        />
      )}
    </Container>
  );
};

export {ToolResultMessage};
