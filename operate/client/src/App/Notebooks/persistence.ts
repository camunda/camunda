/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';
import {
  NotebookSchema,
  NotebookIndexEntrySchema,
  type Notebook,
  type NotebookIndexEntry,
} from './types';

const KEYS = {
  index: 'operate.notebooks.v1.index',
  notebook: (id: string) => `operate.notebooks.v1.${id}`,
} as const;

const NotebookIndexSchema = z.array(NotebookIndexEntrySchema);

function loadIndex(): NotebookIndexEntry[] {
  try {
    const raw = localStorage.getItem(KEYS.index);
    if (raw == null) {
      return [];
    }
    return NotebookIndexSchema.parse(JSON.parse(raw));
  } catch {
    return [];
  }
}

function saveIndex(index: NotebookIndexEntry[]): void {
  localStorage.setItem(KEYS.index, JSON.stringify(index));
}

function saveNotebook(notebook: Notebook): void {
  localStorage.setItem(KEYS.notebook(notebook.id), JSON.stringify(notebook));

  const index = loadIndex();
  const entry: NotebookIndexEntry = {
    id: notebook.id,
    title: notebook.title,
    updatedAt: notebook.updatedAt,
    widgetCount: notebook.widgets.length,
  };

  const existing = index.findIndex((e) => e.id === notebook.id);
  if (existing >= 0) {
    index[existing] = entry;
  } else {
    index.push(entry);
  }

  saveIndex(index);
}

function loadNotebook(id: string): Notebook | null {
  try {
    const raw = localStorage.getItem(KEYS.notebook(id));
    if (raw == null) {
      return null;
    }
    const parsed = JSON.parse(raw) as unknown;
    const result = NotebookSchema.safeParse(parsed);
    if (!result.success) {
      return null;
    }
    return result.data;
  } catch {
    return null;
  }
}

function deleteNotebook(id: string): void {
  localStorage.removeItem(KEYS.notebook(id));
  const index = loadIndex().filter((e) => e.id !== id);
  saveIndex(index);
}

function listNotebooks(): NotebookIndexEntry[] {
  return loadIndex().sort((a, b) => b.updatedAt - a.updatedAt);
}

export {saveNotebook, loadNotebook, deleteNotebook, listNotebooks};
