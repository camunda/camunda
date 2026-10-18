/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type StructuredData = Record<string, unknown> | unknown[];

const isRecord = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

const isScalar = (value: unknown) =>
  value === null || (typeof value !== 'object' && typeof value !== 'function');

const DOCUMENT_TAG = /<doc\b[^>]*\/>/g;
const FENCED_JSON = /^```(?:json)?\s*([\s\S]*?)\s*```$/;
const ACRONYMS: Record<string, string> = {
  id: 'ID',
  ids: 'IDs',
  url: 'URL',
  uri: 'URI',
  api: 'API',
  a2a: 'A2A',
  llm: 'LLM',
};

/**
 * Document references are rendered as attachment chips, so the inline
 * `<doc ... />` markers the connector adds for the model are hidden.
 */
const stripDocumentTags = (text: string) =>
  text.replace(DOCUMENT_TAG, '').trim();

/**
 * Returns the parsed value when a text block is a JSON object or array, so it
 * can be shown as labeled fields instead of raw JSON.
 */
const parseStructuredText = (text: string): StructuredData | null => {
  const trimmed = text.trim();
  const source = FENCED_JSON.exec(trimmed)?.[1] ?? trimmed;
  if (!/^[[{]/.test(source)) {
    return null;
  }
  try {
    const value: unknown = JSON.parse(source);
    return isRecord(value) || Array.isArray(value) ? value : null;
  } catch {
    return null;
  }
};

type TextSegment =
  | {kind: 'text'; text: string}
  | {kind: 'structured'; value: StructuredData};

const FENCED_BLOCK = /```(?:json)?[ \t]*\n([\s\S]*?)\n?```/g;

const toStructured = (source: string): StructuredData | null => {
  try {
    const value: unknown = JSON.parse(source);
    return isRecord(value) || Array.isArray(value) ? value : null;
  } catch {
    return null;
  }
};

/** Index just past the balanced JSON value starting at \`start\`, or -1. */
const findJsonEnd = (text: string, start: number) => {
  let depth = 0;
  let inString = false;
  for (let index = start; index < text.length; index++) {
    const char = text[index];
    if (inString) {
      if (char === '\\') {
        index++;
      } else if (char === '"') {
        inString = false;
      }
    } else if (char === '"') {
      inString = true;
    } else if (char === '{' || char === '[') {
      depth++;
    } else if (char === '}' || char === ']') {
      depth--;
      if (depth === 0) {
        return index + 1;
      }
    }
  }
  return -1;
};

const pushText = (segments: TextSegment[], text: string) => {
  const trimmed = text.trim();
  if (trimmed !== '') {
    segments.push({kind: 'text', text: trimmed});
  }
};

const splitBareJson = (text: string, segments: TextSegment[]) => {
  const blockStart = /(^|\n)[ \t]*[[{]/g;
  let cursor = 0;
  let match: RegExpExecArray | null;
  while ((match = blockStart.exec(text)) !== null) {
    const start = match.index + match[0].length - 1;
    const end = findJsonEnd(text, start);
    const value = end > 0 ? toStructured(text.slice(start, end)) : null;
    if (value === null) {
      continue;
    }
    pushText(segments, text.slice(cursor, start));
    segments.push({kind: 'structured', value});
    cursor = end;
    blockStart.lastIndex = end;
  }
  pushText(segments, text.slice(cursor));
};

/**
 * Splits agent text into prose and embedded JSON (fenced \`json\` blocks or
 * bare JSON objects on their own lines), so JSON renders as labeled fields.
 */
const splitStructuredText = (text: string): TextSegment[] => {
  const segments: TextSegment[] = [];
  let cursor = 0;
  for (const match of text.matchAll(FENCED_BLOCK)) {
    const value = toStructured(match[1] ?? '');
    if (value === null) {
      continue;
    }
    splitBareJson(text.slice(cursor, match.index), segments);
    segments.push({kind: 'structured', value});
    cursor = match.index + match[0].length;
  }
  splitBareJson(text.slice(cursor), segments);
  return segments;
};

/** Story evidence (\`{kind: ...}\`) renders as cards, so it is omitted. */
const omitEvidence = (value: unknown): unknown => {
  if (
    !isRecord(value) ||
    !isRecord(value.evidence) ||
    typeof value.evidence.kind !== 'string'
  ) {
    return value;
  }
  const {evidence: _evidence, ...rest} = value;
  return rest;
};

const isEmptyValue = (value: unknown) =>
  value === undefined ||
  (isRecord(value) && Object.keys(value).length === 0) ||
  (Array.isArray(value) && value.length === 0);

const humanizeKey = (key: string) => {
  const words = key
    .replace(/^camunda\./, '')
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2')
    .split(/[\s_.-]+/)
    .filter(Boolean)
    .map((word) => ACRONYMS[word.toLowerCase()] ?? word.toLowerCase());
  const label = words.join(' ');
  return label.charAt(0).toUpperCase() + label.slice(1);
};

const formatScalar = (value: unknown): string => {
  if (value === null || value === undefined || value === '') {
    return '–';
  }
  if (typeof value === 'boolean') {
    return value ? 'Yes' : 'No';
  }
  return String(value);
};

const getDocumentName = (value: unknown): string | null => {
  if (!isRecord(value)) {
    return null;
  }
  const metadata = isRecord(value.metadata) ? value.metadata : null;
  const name = metadata?.fileName ?? value.fileName ?? value.name;
  if (
    typeof name === 'string' &&
    ('documentId' in value || 'camunda.document.type' in value)
  ) {
    return name;
  }
  return null;
};

const formatInline = (value: unknown): string => {
  if (isScalar(value)) {
    return formatScalar(value);
  }
  const documentName = getDocumentName(value);
  if (documentName !== null) {
    return documentName;
  }
  if (Array.isArray(value)) {
    if (value.length === 0) {
      return 'none';
    }
    return value.every(isScalar)
      ? value.map(formatScalar).join(', ')
      : `${value.length} ${value.length === 1 ? 'item' : 'items'}`;
  }
  const entries = Object.entries(value as Record<string, unknown>);
  return `${entries.length} ${entries.length === 1 ? 'field' : 'fields'}`;
};

/**
 * Single-line, human-readable summary of a structured value, used where a
 * compact preview is shown (for example a collapsed tool result).
 */
const summarize = (value: unknown): string => {
  const cleaned = omitEvidence(value);
  if (!isRecord(cleaned)) {
    return formatInline(cleaned);
  }
  return Object.entries(cleaned)
    .map(([key, entry]) => `${humanizeKey(key)}: ${formatInline(entry)}`)
    .join(' · ');
};

export {
  isRecord,
  isScalar,
  isEmptyValue,
  stripDocumentTags,
  parseStructuredText,
  splitStructuredText,
  omitEvidence,
  humanizeKey,
  formatScalar,
  formatInline,
  getDocumentName,
  summarize,
};
export type {StructuredData, TextSegment};
