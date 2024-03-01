/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {ChangeEvent, ReactNode, isValidElement, Children, cloneElement} from 'react';
import {Link} from 'react-router-dom';
import {ArrowsVertical} from '@carbon/icons-react';
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
  MenuItemSelectable,
  DataTableSkeleton,
  Stack,
  Loading,
} from '@carbon/react';
import {MenuButton} from '@camunda/camunda-optimize-composite-components';

import {t} from 'translation';

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

interface ObjectColumn {
  name: string;
  key: string;
  defaultOrder?: 'asc' | 'desc';
  hidden?: boolean;
}

type Column = ReactNode | ObjectColumn;

interface BulkAction {
  selectedEntries: unknown[];
  [x: string]: unknown;
}

interface EntityListProps {
  title?: ReactNode;
  rows: Row[];
  headers: Column[];
  action: ReactNode;
  bulkActions?: React.ReactElement<BulkAction>;
  isLoading?: boolean;
  sorting?: {key: string; order: 'asc' | 'desc'};
  onChange?: (key?: string, order?: 'asc' | 'desc') => void;
  emptyStateComponent?: ReactNode;
}

export default function EntityList({
  title,
  rows,
  headers,
  action,
  bulkActions,
  isLoading,
  sorting,
  onChange,
  emptyStateComponent,
}: EntityListProps) {
  if ((!Array.isArray(rows) || rows?.length === 0) && emptyStateComponent) {
    if (isLoading) {
      return (
        <div className="CarbonEntityList">
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

  const visibleHeaders = headers.filter((header) => !isObjectHeader(header) || !header.hidden);
  const dataTableHeaders = visibleHeaders.map(mapHeaderToDataTableHeader);
  const dataTableRows = rows.map(mapRowToDataTableRow);
  const hasLessThanThreeActions = rows.every(({actions}) => !actions || actions.length <= 2);

  if (isLoading) {
    return (
      <div className="CarbonEntityList">
        <DataTableSkeleton rowCount={dataTableRows.length} headers={dataTableHeaders} />
      </div>
    );
  }

  return (
    <div className="CarbonEntityList">
      <DataTable
        rows={dataTableRows}
        headers={dataTableHeaders}
        isSortable={!!sorting}
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
              return containsSearchWord(cell?.value, searchWord);
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

          return (
            <TableContainer title={title} {...getTableContainerProps()}>
              <TableToolbar {...getToolbarProps()}>
                {bulkActions && (
                  <TableBatchActions {...batchActionProps}>
                    {Children.map(bulkActions, (child, idx) =>
                      cloneElement(child, {
                        key: idx,
                        onDelete: onChange,
                        selectedEntries: rows.filter((row) =>
                          selectedRows.some((selectedRow) => selectedRow.id === row.id)
                        ),
                      })
                    )}
                  </TableBatchActions>
                )}
                <TableToolbarContent aria-hidden={batchActionProps.shouldShowBatchActions}>
                  <TableToolbarSearch
                    onChange={(e) => {
                      onInputChange(e as ChangeEvent<HTMLInputElement>);
                    }}
                    persistent
                  />
                  {sorting && (
                    <MenuButton
                      menuLabel={t('common.sort').toString()}
                      label={<ArrowsVertical />}
                      kind="ghost"
                      hasIconOnly
                      iconDescription={t('common.sort').toString()}
                    >
                      {headers.filter(isObjectHeader).map(({name, key, defaultOrder}) => (
                        <MenuItemSelectable
                          selected={(sorting.key || 'entityType') === key}
                          key={key}
                          onChange={() => onChange?.(key, defaultOrder)}
                          label={name}
                        />
                      ))}
                    </MenuButton>
                  )}
                  {action}
                </TableToolbarContent>
              </TableToolbar>
              <Table {...getTableProps()}>
                <TableHead>
                  <TableRow>
                    {/* @ts-ignore */}
                    {bulkActions && <TableSelectAll {...getSelectionProps()} />}
                    {formattedHeaders.map((header, idx) => (
                      // @ts-ignore
                      <TableHeader
                        {...getHeaderProps({
                          header,
                          isSortable: typeof visibleHeaders[idx] === 'object',
                          onClick: () => {
                            const header = visibleHeaders[idx];
                            if (isObjectHeader(header)) {
                              if (header.key === sorting?.key) {
                                onChange?.(sorting.key, sorting?.order === 'desc' ? 'asc' : 'desc');
                              } else {
                                onChange?.(header.key, header.defaultOrder);
                              }
                            }
                          },
                        })}
                        isSortHeader={header.key === sorting?.key}
                        sortDirection={sorting?.order?.toUpperCase()}
                        className="tableHeader"
                      >
                        {header.header}
                      </TableHeader>
                    ))}
                    <TableHeader />
                  </TableRow>
                </TableHead>
                <TableBody>
                  {formattedRows.map((row, idx) => (
                    <TableRow {...getRowProps({row})}>
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
                  ))}
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
