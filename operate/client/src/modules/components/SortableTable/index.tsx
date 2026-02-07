/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React, {useRef} from 'react';
import {
  Container,
  TableContainer,
  TableCell,
  TableHead,
  EmptyMessageContainer,
  DataTableSkeleton,
  TableExpandRow,
  TableHeadRow,
  TableExpandedRow,
} from './styled';

import {
  DataTable,
  Table,
  type DataTableHeader,
  type DataTableRow,
  Loading,
  TableSelectAll,
  TableSelectRow,
  TableExpandHeader,
} from '@carbon/react';
import {ColumnHeader} from './ColumnHeader';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';
import {EmptyMessage} from '../EmptyMessage';
import {ErrorMessage} from '../ErrorMessage';

const NUMBER_OF_SKELETON_ROWS = 10;

type HeaderColumn = {
  isDefault?: boolean;
  sortKey?: string;
  isDisabled?: boolean;
} & DataTableHeader;

interface Props<_ extends Record<string, unknown>, ColTypes extends unknown[]> {
  state: 'skeleton' | 'loading' | 'error' | 'empty' | 'content';
  selectionType?: 'checkbox' | 'row' | 'none';
  headerColumns: (HeaderColumn & {sortKey?: string})[];
  rows: Omit<DataTableRow<ColTypes>, 'cells'>[];
  emptyMessage?: {message: string; additionalInfo?: string};
  onSelectAll?: () => void;
  onSelect?: (rowId: string) => void;
  checkIsRowSelected?: (rowId: string) => boolean; //must be a function because it depends on a store update: https://mobx.js.org/react-optimizations.html#function-props-
  rowOperationError?: (rowId: string) => React.ReactNode | null; //must be a function because it depends on a store update: https://mobx.js.org/react-optimizations.html#function-props-
  checkIsAllSelected?: () => boolean; //must be a function because it depends on a store update: https://mobx.js.org/react-optimizations.html#function-props-
  checkIsIndeterminate?: () => boolean; //must be a function because it depends on a store update: https://mobx.js.org/react-optimizations.html#function-props-
  onSort?: React.ComponentProps<typeof ColumnHeader>['onSort'];
  columnsWithNoContentPadding?: string[];
  batchOperationId?: string;
  size?: React.ComponentProps<typeof Table>['size'];
  stickyHeader?: boolean;
  onVerticalScrollStartReach?: React.ComponentProps<
    typeof InfiniteScroller
  >['onVerticalScrollStartReach'];
  onVerticalScrollEndReach?: React.ComponentProps<
    typeof InfiniteScroller
  >['onVerticalScrollEndReach'];
}

const SortableTable = <
  RowType extends Record<string, unknown>,
  ColTypes extends React.ReactNode[],
>({
  size = 'sm',
  state,
  selectionType = 'none',
  headerColumns,
  rows,
  emptyMessage,
  onSelectAll,
  onSelect,
  checkIsIndeterminate,
  checkIsAllSelected,
  checkIsRowSelected,
  rowOperationError,
  onVerticalScrollStartReach,
  onVerticalScrollEndReach,
  columnsWithNoContentPadding,
  batchOperationId,
  stickyHeader = false,
  onSort,
}: Props<RowType, ColTypes>) => {
  let scrollableContentRef = useRef<HTMLDivElement | null>(null);

  if (['empty', 'error'].includes(state)) {
    return (
      <EmptyMessageContainer>
        <>
          {state === 'empty' && emptyMessage !== undefined && (
            <EmptyMessage {...emptyMessage} />
          )}
          {state === 'error' && <ErrorMessage />}
        </>
      </EmptyMessageContainer>
    );
  }

  if (state === 'skeleton') {
    return (
      <DataTableSkeleton
        data-testid="data-table-skeleton"
        columnCount={headerColumns.length}
        rowCount={NUMBER_OF_SKELETON_ROWS}
        showHeader={false}
        showToolbar={false}
        headers={headerColumns.map(({header}) => ({
          header,
        }))}
      />
    );
  }

  return (
    <Container ref={scrollableContentRef} $isScrollable={state === 'content'}>
      <DataTable<RowType, ColTypes>
        rows={rows}
        headers={headerColumns}
        size={size}
        render={({
          rows,
          headers,
          getHeaderProps,
          getRowProps,
          getSelectionProps,
          getTableProps,
        }) => (
          <TableContainer $hasError={!!batchOperationId}>
            {state === 'loading' && <Loading data-testid="data-table-loader" />}
            <Table {...getTableProps()} isSortable>
              <TableHead $stickyHeader={stickyHeader}>
                <TableHeadRow>
                  {batchOperationId && (
                    <TableExpandHeader aria-label="expand row" />
                  )}
                  {selectionType === 'checkbox' && (
                    <TableSelectAll
                      {...getSelectionProps()}
                      onSelect={(event) => {
                        getSelectionProps().onSelect(event);
                        onSelectAll?.();
                      }}
                      checked={checkIsAllSelected?.() ?? false}
                      indeterminate={checkIsIndeterminate?.() ?? false}
                    />
                  )}
                  {headers.map((header, index) => {
                    const {key, ...props} = getHeaderProps({
                      header,
                      isSortable: state === 'content',
                    });

                    return (
                      <ColumnHeader
                        {...props}
                        key={key}
                        label={header.header}
                        sortKey={headerColumns[index].sortKey ?? header.key}
                        isDefault={headerColumns[index].isDefault}
                        isDisabled={headerColumns[index].isDisabled}
                        onSort={onSort}
                      />
                    );
                  })}
                </TableHeadRow>
              </TableHead>
              {['content', 'loading'].includes(state) && (
                <InfiniteScroller
                  onVerticalScrollStartReach={onVerticalScrollStartReach}
                  onVerticalScrollEndReach={onVerticalScrollEndReach}
                  scrollableContainerRef={scrollableContentRef}
                >
                  <tbody aria-live="polite" data-testid="data-list">
                    {rows.map((row) => {
                      const isSelected = checkIsRowSelected?.(row.id) ?? false;
                      const errorMessage = rowOperationError?.(row.id);

                      const expandRowStyleClasses = () => {
                        if (batchOperationId) {
                          return errorMessage ? 'errorRow' : 'successRow';
                        }

                        return '';
                      };

                      const {key, ...props} = getRowProps({row});

                      return (
                        <React.Fragment key={row.id}>
                          <TableExpandRow
                            className={expandRowStyleClasses()}
                            {...props}
                            key={key}
                            isSelected={isSelected}
                            $isClickable={selectionType === 'row'}
                            aria-selected={isSelected}
                            onClick={() => {
                              if (selectionType === 'row') {
                                onSelect?.(row.id);
                              }
                            }}
                          >
                            {selectionType === 'checkbox' && (
                              <TableSelectRow
                                {...getSelectionProps({row})}
                                checked={isSelected}
                                onSelect={(event) => {
                                  getSelectionProps({row}).onSelect(event);
                                  onSelect?.(row.id);
                                }}
                              />
                            )}
                            {row.cells.map((cell) => (
                              <TableCell
                                key={cell.id}
                                data-testid={`cell-${cell.info.header}`}
                                $hideCellPadding={columnsWithNoContentPadding?.includes(
                                  cell.info.header,
                                )}
                              >
                                {cell.value}
                              </TableCell>
                            ))}
                          </TableExpandRow>
                          {errorMessage && (
                            <TableExpandedRow colSpan={headers.length + 2}>
                              {errorMessage}
                            </TableExpandedRow>
                          )}
                        </React.Fragment>
                      );
                    })}
                  </tbody>
                </InfiniteScroller>
              )}
            </Table>
          </TableContainer>
        )}
      />
    </Container>
  );
};

export {SortableTable};
