/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE ("USE"), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * "Licensee" means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
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
  DataTableHeader,
  DataTableRow,
  Loading,
  TableSelectAll,
  TableSelectRow,
  TableExpandHeader,
} from '@carbon/react';
import {ColumnHeader} from './ColumnHeader';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';
import {EmptyMessage} from '../EmptyMessage';
import {ErrorMessage} from '../ErrorMessage';
import {getProcessInstancesRequestFilters} from 'modules/utils/filter';

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
  rowOperationError?: (rowId: string) => string | null; //must be a function because it depends on a store update: https://mobx.js.org/react-optimizations.html#function-props-
  checkIsAllSelected?: () => boolean; //must be a function because it depends on a store update: https://mobx.js.org/react-optimizations.html#function-props-
  checkIsIndeterminate?: () => boolean; //must be a function because it depends on a store update: https://mobx.js.org/react-optimizations.html#function-props-
  onSort?: React.ComponentProps<typeof ColumnHeader>['onSort'];
  columnsWithNoContentPadding?: string[];
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
  rowOperationError,
  onVerticalScrollStartReach,
  onVerticalScrollEndReach,
  columnsWithNoContentPadding,
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
                <TableHeadRow>
                  <TableExpandHeader aria-label="expand row" />
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
                      const operationErrorMessage =
                        rowOperationError?.(row.id) || null;

                      const {batchOperationId} =
                        getProcessInstancesRequestFilters();

                      const expandRowStyleClasses = () => {
                        if (operationErrorMessage) {
                          return 'errorRow';
                        } else if (!operationErrorMessage && batchOperationId) {
                          return 'successRow';
                        } else {
                          return '';
                        }
                      };

                      return (
                        <React.Fragment key={row.id}>
                          <TableExpandRow
                            className={expandRowStyleClasses()}
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
                                data-testid={`cell-${cell.info.header}`}
                                $hideCellPadding={columnsWithNoContentPadding?.includes(
                                  cell.info.header,
                                )}
                              >
                                {cell.value}
                              </TableCell>
                            ))}
                          </TableExpandRow>
                          {operationErrorMessage && (
                            <TableExpandedRow colSpan={headers.length + 2}>
                              <div>{operationErrorMessage}</div>
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
