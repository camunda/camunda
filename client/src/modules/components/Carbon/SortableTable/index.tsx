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
} from './styled';

import {
  DataTable,
  Table,
  TableRow,
  DataTableHeader,
  DataTableRow,
} from '@carbon/react';
import {ColumnHeader} from './ColumnHeader';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';
import {EmptyMessage} from '../EmptyMessage';

type HeaderColumn = {
  isDefault?: boolean;
  sortKey?: string;
} & DataTableHeader;

type Props = {
  state: 'skeleton' | 'loading' | 'error' | 'empty' | 'content';
  headerColumns: HeaderColumn[];
  rows: DataTableRow[];
  emptyMessage: {message: string; additionalInfo?: string};
  onSort?: React.ComponentProps<typeof ColumnHeader>['onSort'];
} & Pick<
  React.ComponentProps<typeof InfiniteScroller>,
  'onVerticalScrollStartReach' | 'onVerticalScrollEndReach'
>;

const SortableTable: React.FC<Props> = ({
  state,
  headerColumns,
  rows,
  emptyMessage,
  onVerticalScrollStartReach,
  onVerticalScrollEndReach,
}) => {
  let scrollableContentRef = useRef<HTMLDivElement | null>(null);

  if (['empty', 'error'].includes(state)) {
    return (
      <EmptyMessageContainer>
        <>
          {state === 'empty' && <EmptyMessage {...emptyMessage} />}
          {state === 'error' && (
            <EmptyMessage
              message="Data could not be fetched"
              additionalInfo="Refresh the page to try again"
            />
          )}
        </>
      </EmptyMessageContainer>
    );
  }

  return (
    <Container ref={scrollableContentRef}>
      <DataTable
        useZebraStyles
        rows={rows}
        headers={headerColumns}
        size="sm"
        render={({
          rows,
          headers,
          getHeaderProps,
          getRowProps,
          getTableProps,
        }) => (
          <TableContainer>
            <Table {...getTableProps()} isSortable>
              <TableHead>
                <TableRow>
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
                      />
                    );
                  })}
                </TableRow>
              </TableHead>
              {state === 'content' && (
                <InfiniteScroller
                  onVerticalScrollStartReach={onVerticalScrollStartReach}
                  onVerticalScrollEndReach={onVerticalScrollEndReach}
                  scrollableContainerRef={scrollableContentRef}
                >
                  <tbody aria-live="polite">
                    {rows.map((row) => {
                      return (
                        <TableRow {...getRowProps({row})}>
                          {row.cells.map((cell) => (
                            <TableCell key={cell.id}>{cell.value}</TableCell>
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
