/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AgentInstanceHistoryItem} from '@camunda/camunda-api-zod-schemas/8.10';
import {getContentEvidence} from '../ConversationMessage/EvidenceCard/evidence';
import {
  CompactionContainer,
  CompactionHeading,
  CompactionMessage,
} from './styled';

type Props = {
  content: AgentInstanceHistoryItem['content'];
};

const CompactionEvent: React.FC<Props> = ({content}) =>
  getContentEvidence(content)
    .filter((evidence) => evidence.kind === 'compaction')
    .map((evidence, index) => {
      const savedTokens = evidence['savedTokens'];
      const message = evidence['message'];
      return (
        <CompactionContainer key={index} aria-label="Context compacted">
          <CompactionHeading>
            Context compacted
            {typeof savedTokens === 'number' &&
              Number.isSafeInteger(savedTokens) &&
              savedTokens >= 0 && (
                <span> · Saved {savedTokens.toLocaleString()} tokens</span>
              )}
          </CompactionHeading>
          {typeof message === 'string' && message.trim() !== '' && (
            <CompactionMessage>{message}</CompactionMessage>
          )}
        </CompactionContainer>
      );
    });

export {CompactionEvent};
