/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect, beforeEach} from 'vitest';
import {
  saveNotebook,
  loadNotebook,
  deleteNotebook,
  listNotebooks,
} from './persistence';
import type {Notebook} from './types';

// Minimal in-memory localStorage substitute, reset before each test.
// The global localStorage stub from setupTests is an object with getItem/setItem/removeItem/clear,
// which already satisfies the contract we need here.

const makeNotebook = (overrides: Partial<Notebook> = {}): Notebook => ({
  id: 'nb-001',
  title: 'Test Notebook',
  widgets: [],
  updatedAt: 1_700_000_000_000,
  ...overrides,
});

describe('persistence', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('should save and load a notebook by id', () => {
    // given
    const notebook = makeNotebook({id: 'nb-save-load', title: 'My Notebook'});

    // when
    saveNotebook(notebook);
    const loaded = loadNotebook('nb-save-load');

    // then
    expect(loaded).not.toBeNull();
    expect(loaded?.id).toBe('nb-save-load');
    expect(loaded?.title).toBe('My Notebook');
    expect(loaded?.widgets).toEqual([]);
  });

  it('should return null when loading a non-existent id', () => {
    // given – nothing saved

    // when
    const result = loadNotebook('does-not-exist');

    // then
    expect(result).toBeNull();
  });

  it('should update the index entry when saving (id, title, updatedAt, widgetCount)', () => {
    // given
    const notebook = makeNotebook({
      id: 'nb-index',
      title: 'Indexed Notebook',
      updatedAt: 1_700_000_001_000,
      widgets: [
        {
          id: 'w1',
          type: 'metric',
          title: 'Widget A',
          query: {endpoint: '/v2/process-instances/search', method: 'POST'},
        },
        {
          id: 'w2',
          type: 'table',
          title: 'Widget B',
          query: {endpoint: '/v2/process-instances/search', method: 'POST'},
        },
      ],
    });

    // when
    saveNotebook(notebook);
    const index = listNotebooks();

    // then
    const entry = index.find((e) => e.id === 'nb-index');
    expect(entry).toBeDefined();
    expect(entry?.title).toBe('Indexed Notebook');
    expect(entry?.updatedAt).toBe(1_700_000_001_000);
    expect(entry?.widgetCount).toBe(2);
  });

  it('should remove a notebook from index and storage on delete', () => {
    // given
    const notebook = makeNotebook({id: 'nb-delete'});
    saveNotebook(notebook);
    expect(loadNotebook('nb-delete')).not.toBeNull();

    // when
    deleteNotebook('nb-delete');

    // then
    expect(loadNotebook('nb-delete')).toBeNull();
    const index = listNotebooks();
    expect(index.find((e) => e.id === 'nb-delete')).toBeUndefined();
  });

  it('should return all notebooks from the index sorted by updatedAt desc', () => {
    // given
    saveNotebook(makeNotebook({id: 'nb-old', title: 'Old', updatedAt: 1_000}));
    saveNotebook(makeNotebook({id: 'nb-mid', title: 'Mid', updatedAt: 2_000}));
    saveNotebook(makeNotebook({id: 'nb-new', title: 'New', updatedAt: 3_000}));

    // when
    const index = listNotebooks();

    // then
    expect(index[0]?.id).toBe('nb-new');
    expect(index[1]?.id).toBe('nb-mid');
    expect(index[2]?.id).toBe('nb-old');
  });

  it('should handle malformed JSON gracefully (return null, do not throw)', () => {
    // given – inject bad JSON directly into storage
    localStorage.setItem('operate.notebooks.v1.nb-bad', '{not valid json');

    // when / then – must not throw
    expect(() => loadNotebook('nb-bad')).not.toThrow();
    expect(loadNotebook('nb-bad')).toBeNull();
  });

  it('should validate notebook shape with Zod and return null on schema mismatch', () => {
    // given – valid JSON but wrong shape (missing required fields)
    localStorage.setItem(
      'operate.notebooks.v1.nb-schema-fail',
      JSON.stringify({id: 'nb-schema-fail', notAValidField: true}),
    );

    // when
    const result = loadNotebook('nb-schema-fail');

    // then
    expect(result).toBeNull();
  });
});
