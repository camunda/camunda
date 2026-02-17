/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import React from 'react';
import {Container, TableHeader, TableCell, TableRow} from './styled';
import {
  DataTable as CarbonDataTable,
  Table,
  TableHead,
  TableBody,
  TableContainer,
  TableExpandRow,
  TableExpandHeader,
  TableExpandedRow,
  type DataTableRow,
} from '@carbon/react';

type Props = {
  size?: React.ComponentProps<typeof CarbonDataTable>['size'];
  headers: {key: string; header: string; width?: string}[];
  rows: React.ComponentProps<typeof CarbonDataTable>['rows'];
  className?: string;
  columnsWithNoContentPadding?: string[];
  isExpandable?: boolean;
  expandableRowTitle?: string;
  expandedContents?: {[key: string]: React.ReactNode};
  onRowClick?: (rowId: string) => void;
  checkIsRowSelected?: (rowId: string) => boolean;
};

const TableCells: React.FC<{
  row: DataTableRow<React.ReactNode[]>;
  columnsWithNoContentPadding?: string[];
}> = ({row, columnsWithNoContentPadding}) => {
  return (
    <>
      {row.cells.map((cell) => (
        <TableCell
          key={cell.id}
          $hideCellPadding={columnsWithNoContentPadding?.includes(
            cell.info.header,
          )}
        >
          {cell.value}
        </TableCell>
      ))}
    </>
  );
};

const DataTable = React.forwardRef<HTMLDivElement, Props>(
  (
    {
      size = 'sm',
      headers: rawHeaders,
      rows,
      className,
      columnsWithNoContentPadding,
      isExpandable,
      expandableRowTitle,
      expandedContents,
      onRowClick,
      checkIsRowSelected,
    },
    ref,
  ) => {
    return (
      <Container className={className} ref={ref}>
        <CarbonDataTable size={size} headers={rawHeaders} rows={rows}>
          {({
            rows,
            headers,
            getTableContainerProps,
            getTableProps,
            getRowProps,
          }) => (
            <TableContainer {...getTableContainerProps()}>
              <Table {...getTableProps()}>
                <TableHead>
                  <TableRow>
                    {isExpandable && <TableExpandHeader />}
                    {headers.map((header, index) => (
                      <TableHeader
                        id={header.key}
                        key={header.key}
                        $width={rawHeaders[index].width}
                      >
                        {header.header}
                      </TableHeader>
                    ))}
                  </TableRow>
                </TableHead>

                <TableBody>
                  {rows.map((row) => {
                    if (isExpandable) {
                      const expandedContent = expandedContents?.[row.id];
                      const {key, ...props} = getRowProps({row});
                      return (
                        <React.Fragment key={row.id}>
                          <TableExpandRow
                            {...props}
                            title={expandableRowTitle}
                            id={`expanded-row-${row.id}`}
                          >
                            <TableCells
                              row={row}
                              columnsWithNoContentPadding={
                                columnsWithNoContentPadding
                              }
                            />
                          </TableExpandRow>
                          <TableExpandedRow colSpan={headers.length + 1}>
                            {expandedContent}
                          </TableExpandedRow>
                        </React.Fragment>
                      );
                    }

                    const isSelected = checkIsRowSelected?.(row.id) ?? false;
                    const isClickable = onRowClick !== undefined;
                    const {key, ...props} = getRowProps({row});

                    return (
                      <TableRow
                        {...props}
                        key={row.id}
                        onClick={() => {
                          onRowClick?.(row.id);
                        }}
                        $isClickable={isClickable}
                        isSelected={isSelected}
                        aria-selected={isSelected}
                        tabIndex={isClickable ? 0 : undefined}
                        onKeyDown={({key}) => {
                          if (isClickable && key === 'Enter') {
                            onRowClick?.(row.id);
                          }
                        }}
                      >
                        <TableCells
                          row={row}
                          columnsWithNoContentPadding={
                            columnsWithNoContentPadding
                          }
                        />
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </CarbonDataTable>
      </Container>
    );
  },
);

export {DataTable};
