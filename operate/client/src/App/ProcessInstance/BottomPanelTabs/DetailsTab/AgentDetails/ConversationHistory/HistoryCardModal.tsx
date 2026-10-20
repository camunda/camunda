/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button} from '@carbon/react';
import {useEffect, useRef} from 'react';
import {ArrowLeft, ArrowRight} from '@carbon/react/icons';
import type {
  AgentInstanceHistoryItem,
  AgentTool,
} from '@camunda/camunda-api-zod-schemas/8.10';
import {DocumentContent} from '../ConversationMessage/MessageAttachments/DocumentContent';
import {ToolCalls} from '../ConversationMessage/MessageAttachments/ToolCalls';
import {EvidenceCards} from '../ConversationMessage/EvidenceCard';
import {
  omitEvidence,
  stripDocumentTags,
  isEmptyValue,
} from '../ConversationMessage/StructuredValue/format';
import {ToolResultModal} from '../ConversationMessage/ToolResultMessage/ToolResultModal';
import {MessageDetailsModal} from '../ConversationMessage/MessageDetailsModal';
import {HistoryNavigation} from './styled';

type Props = {
  item: AgentInstanceHistoryItem;
  availableTools: AgentTool[];
  position: number;
  total: number;
  hasEarlier: boolean;
  hasLater: boolean;
  loading: boolean;
  loadError: boolean;
  moreUnloaded: boolean;
  onPrevious: () => void;
  onNext: () => void;
  onClose: () => void;
};

const HistoryCardModal: React.FC<Props> = ({
  item,
  availableTools,
  position,
  total,
  hasEarlier,
  hasLater,
  loading,
  loadError,
  moreUnloaded,
  onPrevious,
  onNext,
  onClose,
}) => {
  const contentRef = useRef<HTMLElement>(null);
  useEffect(() => {
    const content = contentRef.current
      ?.closest('[role="dialog"]')
      ?.querySelector('.cds--modal-content');
    if (content) {
      content.scrollTop = 0;
    }
  }, [item.historyItemKey]);
  useEffect(() => {
    const onKeyDown = (event: KeyboardEvent) => {
      const target = event.target;
      const dialog = contentRef.current?.closest('[role="dialog"]');
      if (
        event.defaultPrevented ||
        event.altKey ||
        event.ctrlKey ||
        event.metaKey ||
        event.shiftKey ||
        loading ||
        !dialog ||
        !(target instanceof HTMLElement) ||
        target.closest('input, textarea, select, [contenteditable="true"]') ||
        (target !== document.body &&
          target.closest('[role="dialog"]') !== dialog)
      ) {
        return;
      }
      if (event.key === 'ArrowLeft' && hasEarlier) {
        event.preventDefault();
        onPrevious();
      } else if (event.key === 'ArrowRight' && hasLater) {
        event.preventDefault();
        onNext();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [hasEarlier, hasLater, loading, onPrevious, onNext]);
  const toolCall = item.role === 'TOOL_RESULT' ? item.toolCalls[0] : undefined;
  const title = toolCall
    ? `Tool call: ${toolCall.toolName}`
    : item.role === 'USER'
      ? 'User message'
      : 'Assistant message';
  const navigation = (
    <HistoryNavigation
      ref={contentRef}
      aria-label="Conversation navigation"
      data-modal-primary-focus
      tabIndex={-1}
    >
      <Button
        kind="ghost"
        size="sm"
        aria-label="Previous card"
        disabled={!hasEarlier || loading}
        onClick={onPrevious}
      >
        <ArrowLeft />
      </Button>
      <span role="status">
        {loading
          ? 'Loading history…'
          : `${position} / ${total}${moreUnloaded ? '+' : ''}`}
      </span>
      <Button
        kind="ghost"
        size="sm"
        aria-label="Next card"
        disabled={!hasLater || loading}
        onClick={onNext}
      >
        <ArrowRight />
      </Button>
    </HistoryNavigation>
  );
  if (toolCall) {
    return (
      <ToolResultModal
        toolName={toolCall.toolName}
        description={
          availableTools.find((tool) => tool.name === toolCall.toolName)
            ?.description ?? null
        }
        input={toolCall.arguments}
        content={item.content}
        onClose={onClose}
        navigation={
          <>
            {navigation}
            {loadError && (
              <span role="alert">
                Could not load more history. Use the arrow to retry.
              </span>
            )}
          </>
        }
      />
    );
  }
  const content = item.content
    .flatMap((entry) => {
      if (entry.contentType === 'TEXT') {
        return [stripDocumentTags(entry.text)];
      }
      if (entry.contentType === 'OBJECT') {
        const value = omitEvidence(entry.object);
        return isEmptyValue(value) ? [] : [JSON.stringify(value, null, 2)];
      }
      return [];
    })
    .join('\n\n');
  return (
    <MessageDetailsModal
      title={title}
      language="markdown"
      content={content}
      onClose={onClose}
      navigation={
        <>
          {navigation}
          {loadError && (
            <span role="alert">
              Could not load more history. Use the arrow to retry.
            </span>
          )}
        </>
      }
      attachments={
        <>
          <DocumentContent
            content={item.content}
            modalTitleSuffix="conversation message"
          />
          <EvidenceCards content={item.content} />
          <ToolCalls toolCalls={item.toolCalls} />
        </>
      }
    />
  );
};

export {HistoryCardModal};
