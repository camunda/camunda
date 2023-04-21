/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {Container, TableContainer, TableCell, TableHead} from './styled';

import {
  DataTable,
  Table,
  TableRow,
  TableHeader,
  DataTableHeader,
  DataTableRow,
  TableBody,
} from '@carbon/react';

type Props = {
  headerColumns: DataTableHeader[];
  rows: DataTableRow[];
};

const SortableTable: React.FC<Props> = ({headerColumns, rows}) => {
  return (
    <Container>
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
                      <TableHeader {...getHeaderProps({header})}>
                        {header.header}
                      </TableHeader>
                    );
                  })}
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((row) => {
                  return (
                    <TableRow {...getRowProps({row})}>
                      {row.cells.map((cell) => (
                        <TableCell key={cell.id}>{cell.value}</TableCell>
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
};

export {SortableTable};
