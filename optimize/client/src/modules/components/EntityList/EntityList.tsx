/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {
  ChangeEvent,
  ReactNode,
  isValidElement,
  Children,
  cloneElement,
  useState,
} from 'react';
import {Link} from 'react-router-dom';
import {
  DataTable,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableHeader,
  TableRow,
  TableToolbar,
  TableToolbarContent,
  TableToolbarSearch,
  TableBatchActions,
  TableSelectAll,
  TableSelectRow,
  DataTableSkeleton,
  Stack,
  Loading,
} from '@carbon/react';

import ListItemAction from './ListItemAction';

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

  const mapHeaderToDataTableHeader = (header: Column, idx: number) =>
    isObjectHeader(header) ? {key: header.key, header: header.name} : {key: idx.toString(), header};

  const mapRowToDataTableRow = (row: Row) => ({
    id: row.id,
    ...(dataTableHeaders[0] && {
      [dataTableHeaders[0]?.key]: (
        <Stack gap={4} orientation="horizontal">
          <div className="entityIcon">{row.icon}</div>
          <Stack gap={2} orientation="vertical">
            {row.link ? (
              <Link title={row.name} className="cds--link" to={row.link}>
                {row.name}
              </Link>
            ) : (
              <span className="rowName">{row.name}</span>
            )}
            <span>{row.type}</span>
          </Stack>
        </Stack>
      ),
    }),
    ...row.meta?.reduce((acc, curr, idx) => {
      const header = dataTableHeaders[idx + 1];
      if (header) {
        return {...acc, [header.key]: curr};
      }

      return acc;
    }, {}),
    // Prevent selecting rows without actions
    disabled: !row.actions?.length,
  });

  const dataTableHeaders = headers.map(mapHeaderToDataTableHeader);
  const dataTableRows = rows.map(mapRowToDataTableRow);
  const hasLessThanThreeActions = rows.every(({actions}) => !actions || actions.length <= 2);
  const objectHeaders = headers.filter(isObjectHeader);
  const isSortable = !!sorting && objectHeaders.filter((header) => header.key).length > 0;

  if (isLoading) {
    return (
      <div className="EntityList">
        <DataTableSkeleton rowCount={dataTableRows.length} headers={dataTableHeaders} />
      </div>
    );
  }

  return (
    <div className="EntityList">
      <DataTable
        rows={dataTableRows}
        headers={dataTableHeaders}
        isSortable={isSortable}
        filterRows={({cellsById, getCellId, headers, inputValue, rowIds}) => {
          const searchWord = inputValue.trim().toLowerCase();
          return rowIds.filter((rowId, idx) =>
            headers.some((header) => {
              const cell = cellsById[getCellId(rowId, header.key)];
              // We allow passing a header without a key
              // so we should also check the index (0 is the first header)
              if (cell?.info?.header === 'name' || cell?.info?.header === '0') {
                const row = rows[idx];
                return (
                  containsSearchWord(row?.name, searchWord) ||
                  containsSearchWord(row?.type, searchWord)
                );
              }
              return containsSearchWord(cell?.value?.toString(), searchWord);
            })
          );
        }}
        size="md"
      >
        {({
          rows: formattedRows,
          headers: formattedHeaders,
          getTableProps,
          getHeaderProps,
          getRowProps,
          getTableContainerProps,
          getToolbarProps,
          getBatchActionProps,
          selectRow,
          getSelectionProps,
          onInputChange,
          selectedRows,
        }) => {
          const batchActionProps = {
            ...getBatchActionProps({
              onSelectAll: () => {
                formattedRows.forEach((row) => {
                  if (!row.isSelected) {
                    selectRow(row.id);
                  }
                });
              },
            }),
          };

          const batchVisible = batchActionProps.shouldShowBatchActions;
          const batchTabIndex = batchVisible ? 0 : -1;
          const tabIndex = batchVisible ? -1 : 0;

          return (
            <TableContainer
              title={title}
              description={
                description instanceof Function
                  ? description(query, formattedRows.length)
                  : description
              }
              {...getTableContainerProps()}
            >
              <TableToolbar {...getToolbarProps()}>
                {bulkActions && (
                  <TableBatchActions {...batchActionProps}>
                    {Children.map(bulkActions, (child, idx) =>
                      cloneElement(child, {
                        key: idx,
                        tabIndex: batchTabIndex,
                        disabled: !batchVisible,
                        onDelete: onChange,
                        selectedEntries: rows.filter((row) =>
                          selectedRows.some((selectedRow) => selectedRow.id === row.id)
                        ),
                      })
                    )}
                  </TableBatchActions>
                )}
                <TableToolbarContent aria-hidden={batchVisible} tabIndex={tabIndex}>
                  <TableToolbarSearch
                    tabIndex={tabIndex}
                    disabled={batchVisible}
                    onChange={(e) => {
                      if (e) {
                        setQuery(e.target.value);
                      }
                      onInputChange(e as ChangeEvent<HTMLInputElement>);
                    }}
                    persistent
                  />
                  {isValidElement<{tabIndex?: number; disabled?: boolean}>(action)
                    ? cloneElement(action, {
                        tabIndex,
                        disabled: batchVisible,
                      })
                    : action}
                </TableToolbarContent>
              </TableToolbar>
              <Table {...getTableProps()}>
                <TableHead>
                  <TableRow>
                    {/* @ts-ignore */}
                    {bulkActions && <TableSelectAll {...getSelectionProps()} />}
                    {formattedHeaders.map((formattedHeader, idx) => {
                      const header = headers[idx];
                      const isHeaderSortable =
                        !!header && typeof header === 'object' && 'key' in header && !!header.key;

                      const {key, ...headerProps} = getHeaderProps({
                        header: formattedHeader,
                        isSortable: isHeaderSortable,
                        onClick: () => {
                          if (isObjectHeader(header)) {
                            if (header.key === sorting?.key) {
                              const {key, order} =
                                getNextSorting(sorting, header.defaultOrder) || {};
                              onChange?.(key, order);
                            } else {
                              onChange?.(header.key, header.defaultOrder);
                            }
                          }
                        },
                      });

                      return (
                        // @ts-ignore
                        <TableHeader
                          key={key}
                          {...headerProps}
                          isSortHeader={formattedHeader.key === sorting?.key}
                          sortDirection={sorting?.order?.toUpperCase()}
                          className="tableHeader"
                        >
                          {formattedHeader.header}
                        </TableHeader>
                      );
                    })}
                    <TableHeader />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {formattedRows.map((row, idx) => {
                    const {key, ...rowProps} = getRowProps({row});

                    return (
                      <TableRow key={key} {...rowProps}>
                        {bulkActions && (
                          // @ts-ignore
                          <TableSelectRow {...getSelectionProps({row})} />
                        )}
                        {row.cells.map((cell) => (
                          <TableCell key={cell.id}>{cell.value}</TableCell>
                        ))}
                        <TableCell className="cds--table-column-menu">
                          <ListItemAction
                            actions={rows[idx]?.actions}
                            // carbon recommend using inline buttons if actions are less than three
                            // see https://carbondesignsystem.com/components/data-table/usage/#inline-actions
                            showInlineIconButtons={hasLessThanThreeActions}
                          />
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          );
        }}
      </DataTable>
    </div>
  );
}

function isObjectHeader(header: Column): header is ObjectColumn {
  return header instanceof Object && !isValidElement(header);
}

function containsSearchWord(value = '', searchWord: string) {
  return (typeof value !== 'string' ? '' : value).trim().toLowerCase().includes(searchWord);
}

function getNextSorting({key, order}: Sorting, defaultOrder?: SortingOrder): Sorting | undefined {
  // In case of default order being desc, we need to invert the order
  if (defaultOrder === 'desc') {
    if (order === 'desc') {
      return {key, order: 'asc'};
    } else if (order === 'asc') {
      return;
    } else {
      return {key, order: 'desc'};
    }
  }

  if (order === 'asc') {
    return {key, order: 'desc'};
  } else if (order === 'desc') {
    return;
  } else {
    return {key, order: 'asc'};
  }
}
