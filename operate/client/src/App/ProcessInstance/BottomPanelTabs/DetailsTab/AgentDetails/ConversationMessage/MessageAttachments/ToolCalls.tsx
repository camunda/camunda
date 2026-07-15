/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {memo, useId} from 'react';
import type {AgentInstanceHistoryItem} from '@camunda/camunda-api-zod-schemas/8.10';
import {Tools} from '@carbon/react/icons';
import {
  Attachment,
  AttachmentsContainer,
  AttachmentsLabel,
  AttachmentsList,
} from './styled';

type ToolCall = AgentInstanceHistoryItem['toolCalls'][number];

type Props = {
  toolCalls: ToolCall[];
};

const ToolCalls: React.FC<Props> = memo(function DocumentContent({toolCalls}) {
  const id = useId();

  if (toolCalls.length === 0) {
    return null;
  }

  return (
    <AttachmentsContainer>
      <AttachmentsLabel id={id}>Tool calls</AttachmentsLabel>
      <AttachmentsList aria-labelledby={id}>
        {toolCalls.map((tc) => (
          <Attachment key={tc.toolCallId} title={tc.toolName}>
            <Tools size={12} />
            {tc.toolName}
          </Attachment>
        ))}
      </AttachmentsList>
    </AttachmentsContainer>
  );
});

export {ToolCalls};
