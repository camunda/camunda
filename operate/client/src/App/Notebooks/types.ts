/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {z} from 'zod';

const WidgetQuerySchema = z.object({
  endpoint: z.string(),
  method: z.enum(['GET', 'POST']),
  body: z.unknown().optional(),
  pathParams: z.record(z.string(), z.string()).optional(),
});

const WidgetConfigSchema = z.object({
  id: z.string(),
  type: z.enum(['metric', 'table']),
  title: z.string(),
  query: WidgetQuerySchema,
  field: z.string().optional(),
  columns: z.array(z.string()).optional(),
});

const NotebookSchema = z.object({
  id: z.string(),
  title: z.string(),
  widgets: z.array(WidgetConfigSchema),
  updatedAt: z.number(),
});

const NotebookIndexEntrySchema = z.object({
  id: z.string(),
  title: z.string(),
  updatedAt: z.number(),
  widgetCount: z.number(),
});

type WidgetConfig = z.infer<typeof WidgetConfigSchema>;
type Notebook = z.infer<typeof NotebookSchema>;
type NotebookIndexEntry = z.infer<typeof NotebookIndexEntrySchema>;

export {
  WidgetConfigSchema,
  NotebookSchema,
  NotebookIndexEntrySchema,
  WidgetQuerySchema,
};
export type {WidgetConfig, Notebook, NotebookIndexEntry};
