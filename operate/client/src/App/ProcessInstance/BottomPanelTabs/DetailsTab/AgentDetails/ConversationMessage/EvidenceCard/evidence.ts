/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AgentInstanceHistoryItem} from '@camunda/camunda-api-zod-schemas/8.10';

type ContentItem = AgentInstanceHistoryItem['content'][number];
type Evidence = {kind: string} & Record<string, unknown>;

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

/**
 * Extracts story evidence recorded by the connectors next to (or inside) an
 * OBJECT content block. A2A tool results carry their evidence inline with the
 * task result, so the task id and state are lifted from the result itself.
 */
const getEvidence = (object: unknown): Evidence[] => {
  if (!isRecord(object)) {
    return [];
  }
  const result: Evidence[] = [];
  const evidence = object.evidence;
  if (isRecord(evidence) && typeof evidence.kind === 'string') {
    result.push(
      evidence.kind === 'a2a'
        ? {
            ...evidence,
            taskId: object.taskId,
            state: object.state,
            remoteProcessInstanceKey:
              evidence.remoteProcessInstanceKey ??
              object.remoteProcessInstanceKey,
            remoteOperateUrl:
              evidence.remoteOperateUrl ?? object.remoteOperateUrl,
            kind: 'a2a',
          }
        : (evidence as Evidence),
    );
  }
  if (object.sourceDocumentId !== undefined) {
    result.push({
      kind: 'document',
      sourceDocument: object.sourceDocument,
      sourceDocumentId: object.sourceDocumentId,
    });
  }
  return result;
};

const getContentEvidence = (content: ContentItem[]): Evidence[] =>
  content.flatMap((item) =>
    item.contentType === 'OBJECT' ? getEvidence(item.object) : [],
  );

const isEvidenceOnly = (item: ContentItem) =>
  item.contentType === 'OBJECT' &&
  isRecord(item.object) &&
  Object.keys(item.object).length === 1 &&
  isRecord(item.object.evidence);

export {getContentEvidence, isEvidenceOnly, isRecord};
export type {ContentItem, Evidence};
