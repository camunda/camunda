/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  humanizeKey,
  omitEvidence,
  parseStructuredText,
  splitStructuredText,
  stripDocumentTags,
  summarize,
} from './format';

describe('StructuredValue format', () => {
  it('should strip inline document tags', () => {
    expect(
      stripDocumentTags(
        'See <doc id="1" fileName="a.csv" contentType="text/csv" /> here',
      ),
    ).toBe('See  here');
    expect(stripDocumentTags('<doc id="1" fileName="a.csv" />')).toBe('');
  });

  it('should parse JSON text, including fenced blocks', () => {
    expect(parseStructuredText('{"a": 1}')).toEqual({a: 1});
    expect(parseStructuredText('```json\n[1, 2]\n```')).toEqual([1, 2]);
    expect(parseStructuredText('Plain {text}')).toBeNull();
    expect(parseStructuredText('{not json')).toBeNull();
  });

  it('should remove document-only code wrappers without leaving empty backticks', () => {
    expect(
      stripDocumentTags(
        'Investigate the claim.\n`<doc id="1" fileName="statement.pdf" />`\n`<doc id="2" fileName="SKILL.md" />`',
      ),
    ).toBe('Investigate the claim.');
    expect(stripDocumentTags('``<doc id="1" />``')).toBe('');
    expect(
      stripDocumentTags('Use `deadline.py` with <doc id="1" /> here'),
    ).toBe('Use `deadline.py` with  here');
    expect(stripDocumentTags('`Keep <doc id="1" /> this text`')).toBe(
      '`Keep  this text`',
    );
  });

  it('should humanize keys', () => {
    expect(humanizeKey('refundAmount')).toBe('Refund amount');
    expect(humanizeKey('taskId')).toBe('Task ID');
    expect(humanizeKey('source_document_id')).toBe('Source document ID');
  });

  it('should summarize values without evidence or JSON syntax', () => {
    expect(
      summarize({
        decision: 'APPROVE',
        duplicateConfirmed: true,
        matches: [{a: 1}, {a: 2}],
        tags: ['x', 'y'],
        sourceDocument: {
          documentId: 'd-1',
          metadata: {fileName: 'transactions.csv'},
        },
        evidence: {kind: 'a2a'},
      }),
    ).toBe(
      'Decision: APPROVE · Duplicate confirmed: Yes · Matches: 2 items · Tags: x, y · Source document: transactions.csv',
    );
  });

  it('should split prose from bare and fenced JSON blocks', () => {
    expect(
      splitStructuredText(
        'Here is the decision:\n\n{\n  "decision": "APPROVE",\n  "note": "a } b"\n}',
      ),
    ).toEqual([
      {kind: 'text', text: 'Here is the decision:'},
      {kind: 'structured', value: {decision: 'APPROVE', note: 'a } b'}},
    ]);
    expect(
      splitStructuredText('Respond with:\n\n```json\n{"a": [1]}\n```\nThanks'),
    ).toEqual([
      {kind: 'text', text: 'Respond with:'},
      {kind: 'structured', value: {a: [1]}},
      {kind: 'text', text: 'Thanks'},
    ]);
    expect(splitStructuredText('Use [brackets] inline {x}')).toEqual([
      {kind: 'text', text: 'Use [brackets] inline {x}'},
    ]);
  });

  it('should only omit story evidence records', () => {
    expect(omitEvidence({a: 1, evidence: {kind: 'a2a'}})).toEqual({a: 1});
    expect(omitEvidence({a: 1, evidence: ['TX-1']})).toEqual({
      a: 1,
      evidence: ['TX-1'],
    });
  });
});
