/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {
  DataTable,
  TableContainer,
  TableExpandHeader,
  TableHeader,
  TableRow,
  type DataTableRenderProps,
} from '@carbon/react';
import {
  Table,
  TableExpandRow,
  ExpandableTableCell,
  TableExpandedRow,
  TableHead,
} from '../../PartiallyExpandableDataTable/styled';
import {InfiniteScroller} from 'modules/components/InfiniteScroller';

type Row = {
  id: string;
  [key: string]: React.ReactNode;
};

type Props = {
  headers: {key: string; header: string; width?: string}[];
  rows: Row[];
  className?: string;
  expandedContents?: {
    [key: string]: React.ReactElement<{tabIndex: number}>;
  };
  dataTestId?: string;
  onVerticalScrollStartReach?: React.ComponentProps<
    typeof InfiniteScroller
  >['onVerticalScrollStartReach'];
  onVerticalScrollEndReach?: React.ComponentProps<
    typeof InfiniteScroller
  >['onVerticalScrollEndReach'];
  scrollableContainerRef: React.RefObject<HTMLElement | null>;
};

const PartiallyExpandableDataTable: React.FC<Props> = ({
  headers,
  rows,
  expandedContents,
  dataTestId,
  onVerticalScrollStartReach,
  onVerticalScrollEndReach,
  scrollableContainerRef,
}) => {
  const tableBodyContent = (
    rows: DataTableRenderProps<Row, React.ReactNode[]>['rows'],
    headers: DataTableRenderProps<Row, React.ReactNode[]>['headers'],
    getRowProps: DataTableRenderProps<Row, React.ReactNode[]>['getRowProps'],
    getExpandedRowProps: DataTableRenderProps<
      Row,
      React.ReactNode[]
    >['getExpandedRowProps'],
  ) => {
    return rows.map((row, index: number) => {
      const expandedContent = expandedContents?.[row.id];

      const isExpandable =
        expandedContent !== undefined && React.isValidElement(expandedContent);

      const {key, ...props} = getRowProps({row});
      return (
        <React.Fragment key={row.id}>
          <TableExpandRow
            {...props}
            data-testid={`${dataTestId}-${index}`}
            $isExpandable={isExpandable}
          >
            {row.cells.map((cell) => (
              <ExpandableTableCell key={cell.id}>
                {cell.value}
              </ExpandableTableCell>
            ))}
          </TableExpandRow>

          {isExpandable && (
            <TableExpandedRow
              colSpan={headers.length + 1}
              {...getExpandedRowProps({row})}
            >
              {row.isExpanded
                ? React.cloneElement(expandedContent, {
                    tabIndex: 0,
                  })
                : null}
            </TableExpandedRow>
          )}
        </React.Fragment>
      );
    });
  };

  return (
    <DataTable<Row, React.ReactNode[]>
      size="sm"
      headers={headers}
      rows={rows}
      children={({
        rows,
        headers,
        getTableContainerProps,
        getTableProps,
        getRowProps,
        getHeaderProps,
        getExpandHeaderProps,
        getExpandedRowProps,
      }) => (
        <TableContainer {...getTableContainerProps()} data-testid={dataTestId}>
          <Table {...getTableProps()}>
            <TableHead>
              <TableRow>
                <TableExpandHeader {...getExpandHeaderProps()} />
                {headers.map((header) => {
                  const {key, ...props} = getHeaderProps({header});

                  return (
                    <TableHeader key={key} {...props}>
                      {header.header}
                    </TableHeader>
                  );
                })}
              </TableRow>
            </TableHead>
            <InfiniteScroller
              onVerticalScrollStartReach={onVerticalScrollStartReach}
              onVerticalScrollEndReach={onVerticalScrollEndReach}
              scrollableContainerRef={scrollableContainerRef}
            >
              <tbody>
                {tableBodyContent(
                  rows,
                  headers,
                  getRowProps,
                  getExpandedRowProps,
                )}
              </tbody>
            </InfiniteScroller>
          </Table>
        </TableContainer>
      )}
    />
  );
};

export {PartiallyExpandableDataTable};
