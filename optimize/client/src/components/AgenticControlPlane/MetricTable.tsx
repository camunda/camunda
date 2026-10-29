/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ReactNode} from 'react';
import {Table, TableBody, TableCell, TableHead, TableHeader, TableRow} from '@carbon/react';

import {formatters} from 'services';
import {t} from 'translation';

import './MetricTable.scss';

export interface MetricColumn<T> {
  titleKey: string;
  numeric?: boolean;
  render: (row: T) => ReactNode;
}

interface MetricTableProps<T> {
  className?: string;
  columns: MetricColumn<T>[];
  rows: T[];
  rowKey: (row: T, index: number) => string;
  emptyKey: string;
}

// Plain read-only table for the agent and process tables, which the backend already ranks.
export function MetricTable<T>({className, columns, rows, rowKey, emptyKey}: MetricTableProps<T>) {
  if (rows.length === 0) {
    return <p className={`MetricTable empty ${className ?? ''}`}>{t(emptyKey)}</p>;
  }

  return (
    <div className={`MetricTable ${className ?? ''}`}>
      <Table size="md" useZebraStyles={false}>
        <TableHead>
          <TableRow>
            {columns.map(({titleKey, numeric}) => (
              <TableHeader key={titleKey} className={numeric ? 'numeric' : undefined}>
                {t(titleKey)}
              </TableHeader>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row, index) => (
            <TableRow key={rowKey(row, index)}>
              {columns.map(({titleKey, numeric, render}) => (
                <TableCell key={titleKey} className={numeric ? 'numeric' : undefined}>
                  {render(row)}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </div>
  );
}

const EMPTY = '–';

export function formatPercentage(value: number | null | undefined) {
  return value == null ? EMPTY : formatters.percentage(value);
}

export function formatDuration(ms: number | null | undefined) {
  return ms == null ? EMPTY : formatters.duration(ms, 2);
}

export function formatTokens(value: number | null | undefined) {
  return value == null ? EMPTY : formatters.compact(value);
}

export function formatCount(value: number | null | undefined) {
  return value == null ? EMPTY : formatters.frequency(value);
}

export function formatDecimal(value: number | null | undefined) {
  return value == null ? EMPTY : value.toFixed(1);
}
