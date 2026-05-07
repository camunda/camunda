/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import * as React from 'react';

type SortDirection = 'NONE' | 'ASC' | 'DESC';

const NEXT_SORT: Record<SortDirection, SortDirection> = {
  NONE: 'ASC',
  ASC: 'DESC',
  DESC: 'NONE',
};

type DataTableSize = 'xs' | 'sm' | 'md' | 'lg' | 'xl';

type DataTableHeader = {
  key: string;
  header: React.ReactNode;
  isSortable?: boolean;
  // Carbon's `slug`/`decorator` are AI-label affordances. We accept them so
  // existing call-sites keep compiling but currently ignore them in render.
  slug?: React.ReactElement;
  decorator?: React.ReactElement;
};

type DataTableCell<T> = {
  id: string;
  value: T;
  isEditable: boolean;
  isEditing: boolean;
  isValid: boolean;
  errors: null | Error[];
  info: {header: string};
  hasAILabelHeader?: boolean;
};

type DataTableCells<T extends unknown[]> = {[K in keyof T]: DataTableCell<T[K]>};

type DataTableRow<ColTypes extends unknown[]> = {
  id: string;
  cells: DataTableCells<ColTypes>;
  disabled?: boolean;
  isExpanded?: boolean;
  isSelected?: boolean;
};

type DataTableRowInput = {
  id: string;
  disabled?: boolean;
  isExpanded?: boolean;
  isSelected?: boolean;
  [key: string]: unknown;
};

type SortRowFn = (
  cellA: unknown,
  cellB: unknown,
  options: {
    key: string;
    sortDirection: SortDirection;
    sortStates: Record<SortDirection, SortDirection>;
    locale: string;
    compare: (a: string | number, b: string | number, locale?: string) => number;
  },
) => number;

const defaultCompare = (
  a: string | number,
  b: string | number,
  locale: string = 'en',
) => {
  if (typeof a === 'number' && typeof b === 'number') return a - b;
  return String(a).localeCompare(String(b), locale, {numeric: true});
};

const defaultSortRow: SortRowFn = (cellA, cellB, {sortDirection, locale, compare}) => {
  if (cellA == null && cellB == null) return 0;
  if (cellA == null) return sortDirection === 'ASC' ? -1 : 1;
  if (cellB == null) return sortDirection === 'ASC' ? 1 : -1;
  const a = cellA as string | number;
  const b = cellB as string | number;
  return sortDirection === 'DESC' ? compare(b, a, locale) : compare(a, b, locale);
};

type DataTableRenderProps<RowType, ColTypes extends unknown[]> = {
  headers: DataTableHeader[];
  rows: (DataTableRow<ColTypes> & RowType)[];
  selectedRows: (DataTableRow<ColTypes> & RowType)[];
  getHeaderProps: (options: {
    header: DataTableHeader;
    isSortable?: boolean;
    onClick?: (
      event: React.MouseEvent<HTMLButtonElement>,
      sortState: {sortHeaderKey: string; sortDirection: SortDirection},
    ) => void;
    [key: string]: unknown;
  }) => {
    key: string;
    isSortable: boolean | undefined;
    isSortHeader: boolean;
    sortDirection: SortDirection;
    onClick: (event: React.MouseEvent<HTMLButtonElement>) => void;
    [key: string]: unknown;
  };
  getExpandHeaderProps: (options?: {
    onClick?: (
      event: React.MouseEvent<HTMLButtonElement>,
      expandState: {isExpanded?: boolean},
    ) => void;
    onExpand?: (event: React.MouseEvent<HTMLButtonElement>) => void;
    [key: string]: unknown;
  }) => {
    'aria-label': string;
    id: string;
    isExpanded: boolean;
    onExpand: (event: React.MouseEvent<HTMLButtonElement>) => void;
    [key: string]: unknown;
  };
  getRowProps: (options: {
    onClick?: (event: React.MouseEvent<HTMLButtonElement>) => void;
    row: DataTableRow<ColTypes>;
    [key: string]: unknown;
  }) => {
    'aria-label': string;
    key: string;
    expandHeader: string;
    disabled: boolean | undefined;
    isExpanded?: boolean;
    isSelected?: boolean;
    onExpand: (event: React.MouseEvent<HTMLButtonElement>) => void;
    [key: string]: unknown;
  };
  getExpandedRowProps: (options: {
    row: DataTableRow<ColTypes>;
    [key: string]: unknown;
  }) => {
    id: string;
    [key: string]: unknown;
  };
  getSelectionProps: (options?: {
    onClick?: (event: React.MouseEvent<HTMLInputElement>) => void;
    row?: DataTableRow<ColTypes>;
    [key: string]: unknown;
  }) => {
    'aria-label': string;
    id: string;
    name: string;
    checked?: boolean;
    disabled?: boolean;
    indeterminate?: boolean;
    radio?: boolean;
    onSelect: (event: React.MouseEvent<HTMLInputElement>) => void;
    [key: string]: unknown;
  };
  getToolbarProps: (options?: {[key: string]: unknown}) => {
    size: 'sm' | undefined;
    [key: string]: unknown;
  };
  getBatchActionProps: (options?: {[key: string]: unknown}) => {
    onCancel: () => void;
    onSelectAll?: () => void;
    shouldShowBatchActions: boolean;
    totalCount: number;
    totalSelected: number;
    [key: string]: unknown;
  };
  getTableProps: () => {
    experimentalAutoAlign?: boolean;
    isSortable?: boolean;
    overflowMenuOnHover: boolean;
    size: DataTableSize;
    stickyHeader?: boolean;
    useStaticWidth?: boolean;
    useZebraStyles?: boolean;
  };
  getTableContainerProps: () => {
    stickyHeader?: boolean;
    useStaticWidth?: boolean;
  };
  getCellProps: (options: {cell: DataTableCell<ColTypes>}) => {
    key: string;
    hasAILabelHeader?: boolean;
    [key: string]: unknown;
  };
  onInputChange: (
    event: React.ChangeEvent<HTMLInputElement> | string,
    defaultValue?: string,
  ) => void;
  sortBy: (headerKey: string) => void;
  selectAll: () => void;
  selectRow: (rowId: string) => void;
  expandRow: (rowId: string) => void;
  expandAll: () => void;
  radio: boolean | undefined;
};

type DataTableProps<RowType, ColTypes extends unknown[]> = {
  rows: DataTableRowInput[];
  headers: DataTableHeader[];
  size?: DataTableSize;
  isSortable?: boolean;
  locale?: string;
  overflowMenuOnHover?: boolean;
  radio?: boolean;
  stickyHeader?: boolean;
  useStaticWidth?: boolean;
  useZebraStyles?: boolean;
  experimentalAutoAlign?: boolean;
  sortRow?: SortRowFn;
  filterRows?: (options: {
    cellsById: Record<string, DataTableCell<ColTypes[number]>>;
    getCellId: (rowId: string, header: string) => string;
    headers: DataTableHeader[];
    inputValue: string;
    rowIds: string[];
  }) => string[];
  translateWithId?: (id: string) => string;
  // Either `children` (modern) or `render` (deprecated) — we accept both.
  children?: (
    renderProps: DataTableRenderProps<RowType, ColTypes>,
  ) => React.ReactElement;
  render?: (
    renderProps: DataTableRenderProps<RowType, ColTypes>,
  ) => React.ReactElement;
};

const cellKey = (rowId: string, headerKey: string) => `${rowId}:${headerKey}`;

const buildCellsById = (
  rows: DataTableRowInput[],
  headers: DataTableHeader[],
): Record<string, DataTableCell<unknown>> => {
  const out: Record<string, DataTableCell<unknown>> = {};
  for (const row of rows) {
    for (const header of headers) {
      out[cellKey(row.id, header.key)] = {
        id: cellKey(row.id, header.key),
        value: (row as Record<string, unknown>)[header.key],
        isEditable: false,
        isEditing: false,
        isValid: true,
        errors: null,
        info: {header: header.key},
      };
    }
  }
  return out;
};

function DataTable<RowType, ColTypes extends unknown[]>({
  rows,
  headers,
  size = 'md',
  isSortable,
  locale = 'en',
  overflowMenuOnHover = true,
  radio,
  stickyHeader,
  useStaticWidth,
  useZebraStyles,
  experimentalAutoAlign,
  sortRow = defaultSortRow,
  filterRows,
  children,
  render,
}: DataTableProps<RowType, ColTypes>): React.ReactElement | null {
  const [sortHeaderKey, setSortHeaderKey] = React.useState<string | null>(null);
  const [sortDirection, setSortDirection] = React.useState<SortDirection>('NONE');
  const [filterValue, setFilterValue] = React.useState('');
  const [expandedRows, setExpandedRows] = React.useState<Set<string>>(() => {
    const seeded = rows.filter((r) => r.isExpanded).map((r) => r.id);
    return new Set(seeded);
  });
  const [selectedRows, setSelectedRows] = React.useState<Set<string>>(() => {
    const seeded = rows.filter((r) => r.isSelected).map((r) => r.id);
    return new Set(seeded);
  });

  const cellsById = React.useMemo(() => buildCellsById(rows, headers), [rows, headers]);
  const initialRowOrder = React.useMemo(() => rows.map((r) => r.id), [rows]);

  const filteredRowIds = React.useMemo(() => {
    if (!filterRows || filterValue.trim() === '') return initialRowOrder;
    return filterRows({
      cellsById: cellsById as Record<string, DataTableCell<ColTypes[number]>>,
      getCellId: cellKey,
      headers,
      inputValue: filterValue,
      rowIds: initialRowOrder,
    });
  }, [filterRows, filterValue, cellsById, headers, initialRowOrder]);

  const orderedRowIds = React.useMemo(() => {
    if (sortHeaderKey == null || sortDirection === 'NONE') return filteredRowIds;
    const ids = [...filteredRowIds];
    ids.sort((a, b) => {
      const cellA = cellsById[cellKey(a, sortHeaderKey)]?.value;
      const cellB = cellsById[cellKey(b, sortHeaderKey)]?.value;
      return sortRow(cellA, cellB, {
        key: sortHeaderKey,
        sortDirection,
        sortStates: {NONE: 'NONE', ASC: 'ASC', DESC: 'DESC'},
        locale,
        compare: defaultCompare,
      });
    });
    return ids;
  }, [filteredRowIds, sortHeaderKey, sortDirection, cellsById, sortRow, locale]);

  const rowsById = React.useMemo(() => {
    const map: Record<string, DataTableRowInput> = {};
    for (const r of rows) map[r.id] = r;
    return map;
  }, [rows]);

  const renderRows = React.useMemo(() => {
    return orderedRowIds
      .filter((id) => rowsById[id] != null)
      .map((id) => {
        const row = rowsById[id]!;
        const cells = headers.map((h) => cellsById[cellKey(id, h.key)]!);
        return {
          ...(row as RowType),
          id,
          disabled: row.disabled,
          isExpanded: expandedRows.has(id),
          isSelected: selectedRows.has(id),
          cells: cells as unknown as DataTableCells<ColTypes>,
        } as DataTableRow<ColTypes> & RowType;
      });
  }, [orderedRowIds, rowsById, headers, cellsById, expandedRows, selectedRows]);

  const selectedRenderRows = React.useMemo(
    () => renderRows.filter((r) => selectedRows.has(r.id)),
    [renderRows, selectedRows],
  );

  const sortBy = React.useCallback((headerKey: string) => {
    setSortHeaderKey((prevKey) => {
      setSortDirection((prevDir) =>
        prevKey === headerKey ? NEXT_SORT[prevDir] : NEXT_SORT.NONE,
      );
      return headerKey;
    });
  }, []);

  const selectRow = React.useCallback(
    (rowId: string) => {
      setSelectedRows((prev) => {
        const next = new Set(prev);
        if (radio) {
          next.clear();
          if (!prev.has(rowId)) next.add(rowId);
          return next;
        }
        if (next.has(rowId)) next.delete(rowId);
        else next.add(rowId);
        return next;
      });
    },
    [radio],
  );

  const selectAll = React.useCallback(() => {
    setSelectedRows((prev) => {
      const allSelected = prev.size === renderRows.length && renderRows.length > 0;
      if (allSelected) return new Set();
      return new Set(renderRows.map((r) => r.id));
    });
  }, [renderRows]);

  const expandRow = React.useCallback((rowId: string) => {
    setExpandedRows((prev) => {
      const next = new Set(prev);
      if (next.has(rowId)) next.delete(rowId);
      else next.add(rowId);
      return next;
    });
  }, []);

  const allExpanded = expandedRows.size === renderRows.length && renderRows.length > 0;
  const expandAll = React.useCallback(() => {
    setExpandedRows((prev) => {
      const all = prev.size === renderRows.length && renderRows.length > 0;
      if (all) return new Set();
      return new Set(renderRows.map((r) => r.id));
    });
  }, [renderRows]);

  const onInputChange = React.useCallback(
    (event: React.ChangeEvent<HTMLInputElement> | string, defaultValue?: string) => {
      if (typeof event === 'string') {
        setFilterValue(event === '' ? defaultValue ?? '' : event);
        return;
      }
      if (event == null) {
        setFilterValue(defaultValue ?? '');
        return;
      }
      setFilterValue(event.target?.value ?? '');
    },
    [],
  );

  const totalSelected = selectedRows.size;
  const shouldShowBatchActions = totalSelected > 0;

  const renderProps: DataTableRenderProps<RowType, ColTypes> = {
    headers,
    rows: renderRows,
    selectedRows: selectedRenderRows,
    getHeaderProps: ({header, isSortable: headerSortable, onClick, ...rest}) => {
      const headerIsSortable = headerSortable ?? header.isSortable ?? isSortable;
      const isSortHeader = sortHeaderKey === header.key;
      return {
        ...rest,
        key: header.key,
        isSortable: headerIsSortable,
        isSortHeader,
        sortDirection: isSortHeader ? sortDirection : 'NONE',
        onClick: (event) => {
          if (!headerIsSortable) return;
          sortBy(header.key);
          onClick?.(event, {
            sortHeaderKey: header.key,
            sortDirection: NEXT_SORT[isSortHeader ? sortDirection : 'NONE'],
          });
        },
      };
    },
    getExpandHeaderProps: (options = {}) => {
      const {onClick, onExpand, ...rest} = options;
      return {
        ...rest,
        'aria-label': allExpanded ? 'Collapse all rows' : 'Expand all rows',
        id: 'expand-all',
        isExpanded: allExpanded,
        onExpand: (event) => {
          expandAll();
          onExpand?.(event);
          onClick?.(event, {isExpanded: !allExpanded});
        },
      };
    },
    getRowProps: ({row, onClick, ...rest}) => {
      const isExpanded = expandedRows.has(row.id);
      return {
        ...rest,
        key: row.id,
        'aria-label': isExpanded ? 'Collapse row' : 'Expand row',
        expandHeader: 'expand-all',
        disabled: row.disabled,
        isExpanded,
        isSelected: selectedRows.has(row.id),
        onExpand: (event) => {
          expandRow(row.id);
          onClick?.(event);
        },
      };
    },
    getExpandedRowProps: ({row, ...rest}) => ({
      ...rest,
      id: `expanded-row-${row.id}`,
    }),
    getSelectionProps: (options = {}) => {
      const {row, onClick, ...rest} = options;
      if (row) {
        return {
          ...rest,
          'aria-label': selectedRows.has(row.id) ? 'Deselect row' : 'Select row',
          id: `select-row-${row.id}`,
          name: `select-row-${row.id}`,
          checked: selectedRows.has(row.id),
          disabled: row.disabled,
          radio,
          onSelect: (event) => {
            selectRow(row.id);
            onClick?.(event);
          },
        };
      }
      const allChecked =
        selectedRows.size === renderRows.length && renderRows.length > 0;
      const indeterminate = selectedRows.size > 0 && !allChecked;
      return {
        ...rest,
        'aria-label': allChecked ? 'Deselect all rows' : 'Select all rows',
        id: 'select-all',
        name: 'select-all',
        checked: allChecked,
        indeterminate,
        radio: false,
        onSelect: (event) => {
          selectAll();
          onClick?.(event);
        },
      };
    },
    getToolbarProps: (options = {}) => ({
      ...options,
      size: size === 'sm' || size === 'xs' ? 'sm' : undefined,
    }),
    getBatchActionProps: (options = {}) => ({
      ...options,
      onCancel: () => setSelectedRows(new Set()),
      onSelectAll: selectAll,
      shouldShowBatchActions,
      totalCount: renderRows.length,
      totalSelected,
    }),
    getTableProps: () => ({
      experimentalAutoAlign,
      isSortable,
      overflowMenuOnHover,
      size,
      stickyHeader,
      useStaticWidth,
      useZebraStyles,
    }),
    getTableContainerProps: () => ({
      stickyHeader,
      useStaticWidth,
    }),
    getCellProps: ({cell}) => ({
      key: cell.id,
      hasAILabelHeader: cell.hasAILabelHeader,
    }),
    onInputChange,
    sortBy,
    selectAll,
    selectRow,
    expandRow,
    expandAll,
    radio,
  };

  const renderFn = children ?? render;
  if (!renderFn) return null;
  return renderFn(renderProps);
}

export {DataTable};
export type {
  DataTableProps,
  DataTableRow,
  DataTableHeader,
  DataTableCell,
  DataTableSize,
  DataTableRenderProps,
  SortDirection as DataTableSortState,
  SortRowFn,
};
