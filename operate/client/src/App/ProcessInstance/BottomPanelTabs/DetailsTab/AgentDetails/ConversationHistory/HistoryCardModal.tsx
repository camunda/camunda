/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useMemo, useRef, useState} from 'react';
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
import {HistoryContents} from './HistoryContents';
import {getHistorySteps} from './historySteps';
import type {ModalView} from '../ConversationMessage/ModalView';

type Props = {
  item: AgentInstanceHistoryItem;
  cards: AgentInstanceHistoryItem[];
  availableTools: AgentTool[];
  position: number;
  total: number;
  hasEarlier: boolean;
  hasLater: boolean;
  loading: boolean;
  loadError: boolean;
  moreUnloaded: boolean;
  onSelect: (key: string) => void;
  onPrevious: () => void;
  onNext: () => void;
  onClose: () => void;
};

const HistoryCardModal: React.FC<Props> = ({
  item,
  cards,
  availableTools,
  position,
  total,
  hasEarlier,
  hasLater,
  loading,
  loadError,
  moreUnloaded,
  onSelect,
  onPrevious,
  onNext,
  onClose,
}) => {
  const contentRef = useRef<HTMLElement>(null);
  const [view, setView] = useState<ModalView>('preview');
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
        target.closest(
          'input, textarea, select, [contenteditable="true"], .monaco-editor',
        ) ||
        (target !== document.body &&
          target.closest('[role="dialog"]') !== dialog) ||
        // Content regions keep Arrow Up/Down for scrolling.
        (target.closest('.cds--modal-content') &&
          !target.closest('[aria-label="History contents"]'))
      ) {
        return;
      }
      if (event.key === 'ArrowUp' && hasEarlier) {
        event.preventDefault();
        onPrevious();
      } else if (event.key === 'ArrowDown' && hasLater) {
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
  const steps = useMemo(() => getHistorySteps(cards), [cards]);
  const contents = (
    <HistoryContents
      ref={contentRef}
      steps={steps}
      selectedKey={item.historyItemKey}
      position={position}
      total={total}
      moreUnloaded={moreUnloaded}
      hasEarlier={hasEarlier}
      hasLater={hasLater}
      loading={loading}
      loadError={loadError}
      onSelect={onSelect}
      onPrevious={onPrevious}
      onNext={onNext}
    />
  );
  if (toolCall) {
    return (
      <ToolResultModal
        view={view}
        onViewChange={setView}
        toolName={toolCall.toolName}
        description={
          availableTools.find((tool) => tool.name === toolCall.toolName)
            ?.description ?? null
        }
        input={toolCall.arguments}
        content={item.content}
        onClose={onClose}
        contents={contents}
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
      view={view}
      onViewChange={setView}
      title={title}
      language="markdown"
      content={content}
      onClose={onClose}
      contents={contents}
      attachments={
        <>
          <DocumentContent
            content={item.content}
            modalTitleSuffix="conversation message"
            showAll
          />
          <EvidenceCards content={item.content} />
          <ToolCalls toolCalls={item.toolCalls} />
        </>
      }
    />
  );
};

export {HistoryCardModal};
