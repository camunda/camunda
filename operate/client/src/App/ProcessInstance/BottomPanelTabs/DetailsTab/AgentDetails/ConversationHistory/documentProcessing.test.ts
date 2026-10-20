/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {AgentInstanceHistoryItem} from '@camunda/camunda-api-zod-schemas/8.10';
import {mockAgentInstanceHistoryItem} from 'modules/mocks/mockAgentInstanceHistoryItem';
import {getDocumentProcessing} from './documentProcessing';

type ContentItem = AgentInstanceHistoryItem['content'][number];
const document = (id: string, fileName: string): ContentItem => ({
  contentType: 'DOCUMENT',
  documentReference: {
    'camunda.document.type': 'camunda',
    documentId: id,
    storeId: 'default',
    contentHash: 'hash',
    metadata: {
      fileName,
      contentType: fileName.endsWith('.pdf') ? 'application/pdf' : 'text/csv',
      size: 123,
      expiresAt: null,
      processDefinitionId: null,
      processInstanceKey: null,
      customProperties: {},
    },
  },
});
const parsing: ContentItem = {
  contentType: 'OBJECT',
  object: {
    evidence: {
      kind: 'documentParsing',
      documents: [{documentId: 'pdf', fileName: 'statement.pdf'}],
    },
  },
};
const source = mockAgentInstanceHistoryItem({
  historyItemKey: '1',
  role: 'USER',
  content: [
    document('pdf', 'statement.pdf'),
    document('skill', 'SKILL.md'),
    parsing,
  ],
});
const exported = mockAgentInstanceHistoryItem({
  historyItemKey: '2',
  role: 'TOOL_RESULT',
  toolCalls: [
    {
      toolCallId: 'export',
      toolName: 'sandbox_export_document',
      elementId: 'sandbox',
      arguments: {path: '/statement.csv'},
    },
  ],
  content: [document('csv', 'statement.csv')],
});

describe('getDocumentProcessing', () => {
  it('groups recorded parsing documents with a later CSV export, without modifying history', () => {
    expect(getDocumentProcessing([source, exported]).get('2')).toEqual({
      sourceKey: '1',
      input: [source.content[0], parsing],
    });
    expect(source.content).toHaveLength(3);
    expect(exported.toolCalls[0]?.toolName).toBe('sandbox_export_document');
  });

  it('does not cross a new user turn', () => {
    const nextUser = mockAgentInstanceHistoryItem({role: 'USER'});
    expect(getDocumentProcessing([source, nextUser, exported]).size).toBe(0);
  });

  it.each(['elementInstanceKey', 'agentInstanceKey'] as const)(
    'does not group unrelated %s values',
    (field) => {
      expect(
        getDocumentProcessing([source, {...exported, [field]: 'other'}]).size,
      ).toBe(0);
    },
  );

  it('does not group non-export calls or non-CSV documents', () => {
    expect(
      getDocumentProcessing([
        source,
        {
          ...exported,
          toolCalls: [{...exported.toolCalls[0]!, toolName: 'other'}],
        },
      ]).size,
    ).toBe(0);
    expect(
      getDocumentProcessing([
        source,
        {...exported, content: [document('pdf-out', 'output.pdf')]},
      ]).size,
    ).toBe(0);
  });

  it('does not infer parsing from an attachment or a filename alone', () => {
    expect(
      getDocumentProcessing([
        {...source, content: [document('pdf', 'statement.pdf')]},
        exported,
      ]).size,
    ).toBe(0);
    expect(
      getDocumentProcessing([
        {...source, content: [document('other-pdf', 'statement.pdf'), parsing]},
        exported,
      ]).size,
    ).toBe(0);
  });

  it('does not fabricate a group when earlier pages are not loaded', () => {
    expect(getDocumentProcessing([exported]).size).toBe(0);
  });

  it('keeps malformed parsing evidence harmless', () => {
    expect(
      getDocumentProcessing([
        {
          ...source,
          content: [
            source.content[0]!,
            {
              contentType: 'OBJECT',
              object: {
                evidence: {kind: 'documentParsing', documents: [null, 1]},
              },
            },
          ],
        },
        exported,
      ]).size,
    ).toBe(0);
  });
});
