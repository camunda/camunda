/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE, YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
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
  DenormalizedRow,
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
  row: DenormalizedRow;
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
      headers,
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
        <CarbonDataTable
          size={size}
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
                    {isExpandable && <TableExpandHeader />}
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
                    if (isExpandable) {
                      const expandedContent = expandedContents?.[row.id];
                      return (
                        <React.Fragment key={row.id}>
                          <TableExpandRow
                            {...getRowProps({row})}
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

                    return (
                      <TableRow
                        {...getRowProps({row})}
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
        />
      </Container>
    );
  },
);

export {DataTable};
