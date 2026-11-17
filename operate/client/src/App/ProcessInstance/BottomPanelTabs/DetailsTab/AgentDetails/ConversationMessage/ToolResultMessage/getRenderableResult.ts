/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AgentInstanceHistoryItem} from '@camunda/camunda-api-zod-schemas/8.10';

type ContentItem = AgentInstanceHistoryItem['content'][number];

type ToolCallResult = {
  value: string;
  language: 'markdown' | 'json';
};

const EMPTY_RESULT_MESSAGE = 'The tool call did not return content.';

/**
 * Extracts a tool call result for rendering in a rich text editor.
 * For now, we expect only one text/object content-item for tool results.
 */
const getToolCallResult = (content: ContentItem[]): ToolCallResult | null => {
  const entry = content.find(
    (item) => item.contentType === 'TEXT' || item.contentType === 'OBJECT',
  );
  switch (entry?.contentType) {
    case 'TEXT':
      return {value: entry.text, language: 'markdown'};
    case 'OBJECT':
      return {value: JSON.stringify(entry.object, null, 2), language: 'json'};
    default:
      return null;
  }
};

/**
 * Extracts a tool call result for rendering in a single-line preview.
 * Returns `null` when no TEXT/OBJECT exists, but DOCUMENT content.
 */
const getResultPreview = (content: ContentItem[]): string | null => {
  const entry = content.find(
    (item) => item.contentType === 'TEXT' || item.contentType === 'OBJECT',
  );
  switch (entry?.contentType) {
    case 'TEXT':
      return entry.text;
    case 'OBJECT':
      return JSON.stringify(entry.object);
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
