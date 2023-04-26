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
  DataTableHeader,
  DataTableRow,
  TableBody,
} from '@carbon/react';
import {ColumnHeader} from './ColumnHeader';

type HeaderColumn = {
  isDefault?: boolean;
  sortKey?: string;
} & DataTableHeader;

type Props = {
  headerColumns: HeaderColumn[];
  rows: DataTableRow[];
  onSort?: React.ComponentProps<typeof ColumnHeader>['onSort'];
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
                      <ColumnHeader
                        {...getHeaderProps({
                          header,
                          isSortable: true,
                        })}
                        label={header.header}
                        sortKey={header.sortKey ?? header.key}
                        isDefault={header.isDefault}
                      />
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
