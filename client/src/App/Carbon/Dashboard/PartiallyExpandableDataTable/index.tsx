/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import React from 'react';
import {
  DataTable,
  TableBody,
  TableContainer,
  TableExpandedRow,
  TableRow,
  TableCell as BaseTableCell,
} from '@carbon/react';
import {Table, TableExpandRow, TableCell} from './styled';

type Props = {
  headers: {key: string; header: string; width?: string}[];
  rows: React.ComponentProps<typeof DataTable>['rows'];
  className?: string;
  expandedContents?: {
    [key: string]: React.ReactElement;
  };
};

const PartiallyExpandableDataTable: React.FC<Props> = ({
  headers,
  rows,
  expandedContents,
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
      }) => (
        <TableContainer {...getTableContainerProps()}>
          <Table {...getTableProps()}>
            <TableBody>
              {rows.map((row) => {
                const expandedContent = expandedContents?.[row.id];

                if (
                  expandedContent !== undefined &&
                  React.isValidElement(expandedContent)
                ) {
                  return (
                    <React.Fragment key={row.id}>
                      <TableExpandRow {...getRowProps({row})}>
                        {row.cells.map((cell) => (
                          <BaseTableCell key={cell.id}>
                            {cell.value}
                          </BaseTableCell>
                        ))}
                      </TableExpandRow>
                      <TableExpandedRow colSpan={headers.length + 1}>
                        {expandedContent}
                      </TableExpandedRow>
                    </React.Fragment>
                  );
                }

                return (
                  <TableRow {...getRowProps({row})}>
                    {row.cells.map((cell) => (
                      <TableCell colSpan={headers.length + 1} key={cell.id}>
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
  );
};

export {PartiallyExpandableDataTable};
