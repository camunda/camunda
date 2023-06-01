/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Container, TableHeader, TableCell} from './styled';
import {
  DataTable as CarbonDataTable,
  Table,
  TableHead,
  TableRow,
  TableBody,
  TableContainer,
} from '@carbon/react';

type Props = {
  headers: {key: string; header: string; width?: string}[];
  rows: React.ComponentProps<typeof CarbonDataTable>['rows'];
  className?: string;
  columnsWithNoContentPadding?: string[];
};

const DataTable = React.forwardRef<HTMLDivElement, Props>(
  ({headers, rows, className, columnsWithNoContentPadding}, ref) => {
    return (
      <Container className={className} ref={ref}>
        <CarbonDataTable
          size="sm"
          headers={headers}
          rows={rows}
          render={({
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
                    {headers.map((header) => (
                      <TableHeader
                        id={header.key}
                        key={header.key}
                        $width={header.width}
                      >
                        {header.header}
                      </TableHeader>
                    ))}
                  </TableRow>
                </TableHead>

                <TableBody>
                  {rows.map((row) => {
                    return (
                      <TableRow {...getRowProps({row})}>
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
                </TableBody>
              </Table>
            </TableContainer>
          )}
        />
      </Container>
    );
  }
);

export {DataTable};
