/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AgentInstanceHistoryItem} from '@camunda/camunda-api-zod-schemas/8.10';
import {
  isEmptyValue,
  omitEvidence,
  parseStructuredText,
  splitStructuredText,
  stripDocumentTags,
  summarize,
} from '../StructuredValue/format';

type ContentItem = AgentInstanceHistoryItem['content'][number];

type ToolCallResult =
  | {kind: 'markdown'; value: string; copyValue: string}
  | {kind: 'structured'; value: unknown; copyValue: string};

const EMPTY_RESULT_MESSAGE = 'The tool call did not return content.';

const findResultEntry = (content: ContentItem[]) =>
  content.find(
    (item) => item.contentType === 'TEXT' || item.contentType === 'OBJECT',
  );

/**
 * Extracts a tool call result for rendering. JSON (object content or JSON
 * text) is returned as structured data so it can be shown as labeled fields.
 * For now, we expect only one text/object content-item for tool results.
 */
const getToolCallResult = (content: ContentItem[]): ToolCallResult | null => {
  const entry = findResultEntry(content);
  switch (entry?.contentType) {
    case 'TEXT': {
      const text = stripDocumentTags(entry.text);
      const structured = parseStructuredText(text);
      if (structured !== null) {
        return {
          kind: 'structured',
          value: structured,
          copyValue: JSON.stringify(structured, null, 2),
        };
      }
      return {kind: 'markdown', value: text, copyValue: text};
    }
    case 'OBJECT': {
      const value = omitEvidence(entry.object);
      if (isEmptyValue(value)) {
        return null;
      }
      return {
        kind: 'structured',
        value,
        copyValue: JSON.stringify(value, null, 2),
      };
    }
    default:
      return null;
  }
};

/**
 * Extracts a tool call result for rendering in a single-line preview.
 * Returns `null` when the result is only documents or story evidence, which
 * render as their own chips and cards.
 */
const getResultPreview = (content: ContentItem[]): string | null => {
  const entry = findResultEntry(content);
  switch (entry?.contentType) {
    case 'TEXT': {
      const text = stripDocumentTags(entry.text);
      return splitStructuredText(text)
        .map((segment) =>
          segment.kind === 'text' ? segment.text : summarize(segment.value),
        )
        .join(' ');
    }
    case 'OBJECT': {
      const value = omitEvidence(entry.object);
      return isEmptyValue(value) ? null : summarize(value);
    }
    default: {
      const hasDocuments = content.some(
        (item) => item.contentType === 'DOCUMENT',
      );
      return hasDocuments ? null : EMPTY_RESULT_MESSAGE;
    }
  }
};

export {getToolCallResult, getResultPreview, EMPTY_RESULT_MESSAGE};
export type {ContentItem, ToolCallResult};
