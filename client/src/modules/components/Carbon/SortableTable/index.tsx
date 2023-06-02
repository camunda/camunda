/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React, {useRef} from 'react';
import {
  Container,
  TableContainer,
  TableCell,
  TableHead,
  EmptyMessageContainer,
  DataTableSkeleton,
  TableRow,
} from './styled';

import {
  DataTable,
  Table,
  DataTableHeader,
  DataTableRow,
  Loading,
  TableSelectAll,
  TableSelectRow,
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

type Props = {
  state: 'skeleton' | 'loading' | 'error' | 'empty' | 'content';
  selectionType?: 'checkbox' | 'row' | 'none';
  headerColumns: HeaderColumn[];
  rows: DataTableRow[];
  emptyMessage?: {message: string; additionalInfo?: string};
  onSelectAll?: () => void;
  onSelect?: (rowId: string) => void;
  checkIsRowSelected?: (rowId: string) => boolean; //must be a function because it depends on a store update: https://mobx.js.org/react-optimizations.html#function-props-
  checkIsAllSelected?: () => boolean; //must be a function because it depends on a store update: https://mobx.js.org/react-optimizations.html#function-props-
  checkIsIndeterminate?: () => boolean; //must be a function because it depends on a store update: https://mobx.js.org/react-optimizations.html#function-props-
  onSort?: React.ComponentProps<typeof ColumnHeader>['onSort'];
  columnsWithNoContentPadding?: string[];
  useZebraStyles?: boolean;
} & Pick<
  React.ComponentProps<typeof InfiniteScroller>,
  'onVerticalScrollStartReach' | 'onVerticalScrollEndReach'
>;

const SortableTable: React.FC<Props> = ({
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
  onVerticalScrollStartReach,
  onVerticalScrollEndReach,
  columnsWithNoContentPadding,
  useZebraStyles,
}) => {
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
          header: header.toString(),
        }))}
      />
    );
  }

  return (
    <Container ref={scrollableContentRef} $isScrollable={state === 'content'}>
      <DataTable
        useZebraStyles={useZebraStyles}
        rows={rows}
        headers={headerColumns}
        size="sm"
        render={({
          rows,
          headers,
          getHeaderProps,
          getRowProps,
          getSelectionProps,
          getTableProps,
        }) => (
          <TableContainer>
            {state === 'loading' && <Loading data-testid="data-table-loader" />}
            <Table {...getTableProps()} isSortable>
              <TableHead>
                <TableRow>
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
                  {headers.map((header) => {
                    return (
                      <ColumnHeader
                        {...getHeaderProps({
                          header,
                          isSortable: state === 'content',
                        })}
                        label={header.header}
                        sortKey={header.sortKey ?? header.key}
                        isDefault={header.isDefault}
                        isDisabled={header.isDisabled}
                      />
                    );
                  })}
                </TableRow>
              </TableHead>
              {['content', 'loading'].includes(state) && (
                <InfiniteScroller
                  onVerticalScrollStartReach={onVerticalScrollStartReach}
                  onVerticalScrollEndReach={onVerticalScrollEndReach}
                  scrollableContainerRef={scrollableContentRef}
                >
                  <tbody aria-live="polite">
                    {rows.map((row) => {
                      const isSelected = checkIsRowSelected?.(row.id) ?? false;

                      return (
                        <TableRow
                          {...getRowProps({row})}
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
                              $hideCellPadding={columnsWithNoContentPadding?.includes(
                                cell.info.header
                              )}
                            >
                              {cell.value}
                            </TableCell>
                          ))}
                        </TableRow>
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
