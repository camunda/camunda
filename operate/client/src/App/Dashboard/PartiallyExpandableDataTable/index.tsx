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
  TableBody,
  TableContainer,
  TableExpandHeader,
  TableHeader,
  TableRow,
} from '@carbon/react';
import {
  Table,
  TableExpandRow,
  ExpandableTableCell,
  TableExpandedRow,
  TableHead,
} from './styled';

type Props = {
  headers: {key: string; header: string; width?: string}[];
  rows: React.ComponentProps<typeof DataTable>['rows'];
  className?: string;
  expandedContents?: {
    [key: string]: React.ReactElement<{tabIndex: number}>;
  };
  dataTestId?: string;
};

const PartiallyExpandableDataTable: React.FC<Props> = ({
  headers,
  rows,
  expandedContents,
  dataTestId,
}) => {
  return (
    <DataTable
      size="sm"
      headers={headers}
      rows={rows}
      render={({
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
            <TableBody>
              {rows.map((row, index) => {
                const expandedContent = expandedContents?.[row.id];

                const isExpandable =
                  expandedContent !== undefined &&
                  React.isValidElement(expandedContent);

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
              })}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    />
  );
};

export {PartiallyExpandableDataTable};
