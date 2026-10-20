/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AgentInstanceHistoryItem} from '@camunda/camunda-api-zod-schemas/8.10';
import {
  getContentEvidence,
  isRecord,
} from '../ConversationMessage/EvidenceCard/evidence';

type ContentItem = AgentInstanceHistoryItem['content'][number];
type DocumentProcessing = {sourceKey: string; input: ContentItem[]};

/**
 * Groups observed steps within one user turn, without claiming document lineage
 * or a provider tool-call status that the history does not report.
 */
const getDocumentProcessing = (
  chronologicalItems: AgentInstanceHistoryItem[],
): Map<string, DocumentProcessing> => {
  const sources = new Map<string, AgentInstanceHistoryItem>();
  const groups = new Map<string, DocumentProcessing>();
  for (const item of chronologicalItems) {
    if (item.role === 'USER') {
      sources.set(item.elementInstanceKey, item);
      continue;
    }
    if (
      item.role !== 'TOOL_RESULT' ||
      item.toolCalls[0]?.toolName !== 'sandbox_export_document' ||
      !item.content.some(
        (entry) =>
          entry.contentType === 'DOCUMENT' &&
          entry.documentReference.metadata.fileName
            ?.toLowerCase()
            .endsWith('.csv'),
      )
    ) {
      continue;
    }
    const source = sources.get(item.elementInstanceKey);
    if (!source || source.agentInstanceKey !== item.agentInstanceKey) {
      continue;
    }
    const parsing = getContentEvidence(source.content).filter(
      (entry) => entry.kind === 'documentParsing',
    );
    const ids = new Set(
      parsing.flatMap((entry) =>
        Array.isArray(entry['documents'])
          ? entry['documents'].filter(isRecord).map((doc) => doc['documentId'])
          : [],
      ),
    );
    const input = source.content.filter(
      (entry) =>
        entry.contentType === 'DOCUMENT' &&
        ids.has(entry.documentReference.documentId) &&
        (entry.documentReference.metadata.contentType === 'application/pdf' ||
          entry.documentReference.metadata.fileName
            ?.toLowerCase()
            .endsWith('.pdf')),
    );
    if (input.length > 0) {
      groups.set(item.historyItemKey, {
        sourceKey: source.historyItemKey,
        input: [
          ...input,
          ...source.content.filter(
            (entry) =>
              entry.contentType === 'OBJECT' &&
              getContentEvidence([entry]).some(
                (evidence) => evidence.kind === 'documentParsing',
              ),
          ),
        ],
      });
    }
  }
  return groups;
};

export {getDocumentProcessing};
