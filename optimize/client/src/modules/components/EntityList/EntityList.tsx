/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {ReactNode, isValidElement, Children, cloneElement, useMemo, useState} from 'react';
import {Link} from 'react-router-dom';
import {Loading} from '@carbon/react';
import {
  DataTable,
  Input,
  type DataTableColumn,
  type DataTableRowAction,
} from '@camunda/design-system';

import {t} from 'translation';

import './EntityList.scss';

export interface Action {
  icon: ReactNode;
  text: ReactNode;
  action: () => void;
}

interface Row {
  id: string;
  type: string;
  link?: string;
  icon: ReactNode;
  name: string;
  meta?: ReactNode[];
  actions: Action[];
}

type SortingOrder = 'asc' | 'desc';

type Sorting = {key: string; order: SortingOrder};

interface ObjectColumn {
  name: string;
  key: string;
  defaultOrder?: SortingOrder;
}

type Column = ReactNode | ObjectColumn;

interface BulkAction {
  selectedEntries: unknown[];
  [x: string]: unknown;
}

interface EntityListProps {
  title?: ReactNode;
  description?: ((query: string | undefined, selectedCount?: number) => ReactNode) | ReactNode;
  rows: Row[];
  headers: Column[];
  action: ReactNode;
  bulkActions?: React.ReactElement<BulkAction>;
  isLoading?: boolean;
  sorting?: Sorting;
  onChange?: (key?: string, order?: SortingOrder) => void;
  emptyStateComponent?: ReactNode;
}

export default function EntityList({
  title,
  description,
  rows,
  headers,
  action,
  bulkActions,
  isLoading,
  sorting,
  onChange,
  emptyStateComponent,
}: EntityListProps) {
  const [query, setQuery] = useState<string | undefined>();
  const [selection, setSelection] = useState<Record<string, boolean>>({});

  const filteredRows = useMemo(() => {
    const searchWord = query?.trim().toLowerCase();
    if (!searchWord) {
      return rows;
    }

    return rows.filter(
      (row) =>
        containsSearchWord(row.name, searchWord) ||
        containsSearchWord(row.type, searchWord) ||
        row.meta?.some((cell) => typeof cell === 'string' && containsSearchWord(cell, searchWord))
    );
  }, [rows, query]);

  if ((!Array.isArray(rows) || rows?.length === 0) && emptyStateComponent) {
    if (isLoading) {
      return (
        <div className="EntityList">
          <Loading className="loadingIndicator" withOverlay={false} />
        </div>
      );
    }

    return emptyStateComponent;
  }

  if (!Array.isArray(rows) || !Array.isArray(headers) || headers?.length <= 0) {
    return null;
  }

  const columns: DataTableColumn<Row>[] = headers.map((header, idx) => ({
    id: isObjectHeader(header) ? header.key : idx.toString(),
    header: () => (isObjectHeader(header) ? header.name : header),
    // Sorting is server-side, but TanStack only offers the control on a column with an accessor.
    accessorFn: (row: Row) => (idx === 0 ? row.name : row.meta?.[idx - 1]),
    enableSorting: isObjectHeader(header) && !!header.key && !!sorting,
    // Honour the caller's first-click direction (e.g. `defaultOrder: 'desc'` on modified columns).
    ...(isObjectHeader(header) && header.defaultOrder
      ? {sortDescFirst: header.defaultOrder === 'desc'}
      : {}),
    cell: ({row}) => {
      if (idx === 0) {
        return (
          <div className="entityCell">
            <div className="entityIcon">{row.original.icon}</div>
            <div className="entityText">
              {row.original.link ? (
                <Link title={row.original.name} className="entityName" to={row.original.link}>
                  {row.original.name}
                </Link>
              ) : (
                <span className="rowName" title={row.original.name}>
                  {row.original.name}
                </span>
              )}
              <span className="entityType">{row.original.type}</span>
            </div>
          </div>
        );
      }

      const value = row.original.meta?.[idx - 1];
      // Anything else is already a rendered node, and cannot carry a `title` tooltip.
      return typeof value === 'string' ? (
        <span className="entityMeta" title={value}>
          {value}
        </span>
      ) : (
        value
      );
    },
  }));

  // The table declares its actions once, so a row with fewer hides the trailing slots.
  const actionSlots = Math.max(0, ...rows.map((row) => row.actions?.length ?? 0));
  const rowActions: DataTableRowAction<Row>[] = Array.from({length: actionSlots}, (_, slot) => ({
    id: `action-${slot}`,
    label: (row) => String(row.actions?.[slot]?.text ?? ''),
    icon: (row) => row.actions?.[slot]?.icon,
    visible: (row) => Boolean(row.actions?.[slot]),
    onClick: (row) => row.actions?.[slot]?.action(),
  }));

  const hasLessThanThreeActions = rows.every(({actions}) => !actions || actions.length <= 2);
  // A row without actions is protected (e.g. the last manager) and must stay non-selectable.
  const selectableRowIds = new Set(rows.filter((row) => row.actions?.length).map((row) => row.id));
  const allSelectableSelected =
    selectableRowIds.size > 0 && [...selectableRowIds].every((id) => selection[id]);
  const selectedRows = rows.filter((row) => selection[row.id]);

  // The DS table exposes no per-row selection predicate, so protected rows are dropped here rather
  // than at the table. Its header "select all" therefore never reaches an all-selected state and can
  // only ever add rows (never emit the empty set that clears them). Read a select-all that re-adds a
  // protected row while every selectable row is already selected as the toggle-off it can't express.
  const changeSelection = (next: Record<string, boolean>) => {
    const readdsProtectedRow = Object.keys(next).some(
      (id) => next[id] && !selectableRowIds.has(id)
    );
    if (readdsProtectedRow && allSelectableSelected) {
      setSelection({});
      return;
    }
    setSelection(
      Object.fromEntries(
        Object.entries(next).filter(([id, selected]) => selected && selectableRowIds.has(id))
      )
    );
  };

  return (
    <div className="EntityList c4-ui">
      {title && <div className="entityTitle">{title}</div>}
      <div className="entityToolbar">
        <Input
          className="entitySearch"
          type="search"
          value={query ?? ''}
          onChange={(event) => setQuery(event.target.value)}
          placeholder={t('home.search.name').toString()}
        />
        <div className="entityToolbarAction">
          {bulkActions && selectedRows.length > 0
            ? Children.map(bulkActions, (child, idx) =>
                cloneElement(child, {
                  key: idx,
                  onDelete: onChange,
                  selectedEntries: selectedRows,
                })
              )
            : action}
        </div>
      </div>
      <DataTable
        columns={columns}
        data={filteredRows}
        description={
          description instanceof Function ? description(query, filteredRows.length) : description
        }
        getRowId={(row) => row.id}
        rowActions={rowActions}
        inlineActionsThreshold={hasLessThanThreeActions ? 2 : 0}
        // Owned here rather than by `batchActions`, so callers' bulk action elements keep working.
        rowSelection={
          bulkActions ? {selectedRowIds: selection, onSelectedRowsChange: changeSelection} : false
        }
        sorting={{
          manual: true,
          sortState: sorting?.key ? [{id: sorting.key, desc: sorting.order === 'desc'}] : [],
          onSortingChange: (state) => {
            const [next] = state;
            onChange?.(next?.id, next && (next.desc ? 'desc' : 'asc'));
          },
        }}
        loading={isLoading}
        loadingRowCount={rows.length}
        // Not `emptyState`: the early return above covers an empty list, and the table would show
        // that onboarding copy for an empty search too.
        pagination
        size="md"
      />
    </div>
  );
}

function isObjectHeader(header: Column): header is ObjectColumn {
  return header instanceof Object && !isValidElement(header);
}

function containsSearchWord(value = '', searchWord: string) {
  return (typeof value !== 'string' ? '' : value).trim().toLowerCase().includes(searchWord);
}
