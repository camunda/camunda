/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {DataTable as CarbonDataTable} from './data-table.carbon';
import {
  DataTable as ShadcnDataTable,
  type DataTableHeader,
} from './data-table.shadcn';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../table/table.shadcn';
import {Checkbox} from '../checkbox/checkbox.shadcn';
import {ChevronDownIcon, ChevronUpIcon, ChevronsUpDownIcon} from 'lucide-react';

const HEADERS: DataTableHeader[] = [
  {key: 'name', header: 'Name', isSortable: true},
  {key: 'protocol', header: 'Protocol', isSortable: true},
  {key: 'port', header: 'Port', isSortable: true},
  {key: 'rule', header: 'Rule'},
];

const ROWS = [
  {id: 'load-balancer-1', name: 'Load Balancer 1', protocol: 'HTTP', port: 80, rule: 'Round robin'},
  {id: 'load-balancer-2', name: 'Load Balancer 2', protocol: 'HTTPS', port: 443, rule: 'DNS delegation'},
  {id: 'load-balancer-3', name: 'Load Balancer 3', protocol: 'HTTP', port: 80, rule: 'Round robin'},
  {id: 'load-balancer-4', name: 'Load Balancer 4', protocol: 'HTTP', port: 8080, rule: 'Round robin'},
  {id: 'load-balancer-5', name: 'Load Balancer 5', protocol: 'HTTPS', port: 443, rule: 'DNS delegation'},
];

const meta: Meta = {
  title: 'UI/DataTable',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonDataTable rows={ROWS} headers={HEADERS}>
          {({rows, headers, getHeaderProps, getRowProps, getTableProps}) => (
            <table {...getTableProps()} className="cds--data-table">
              <thead>
                <tr>
                  {headers.map((header) => {
                    const props = getHeaderProps({header});
                    return <th key={props.key as string}>{header.header}</th>;
                  })}
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => {
                  const props = getRowProps({row});
                  return (
                    <tr key={props.key as string}>
                      {row.cells.map((cell) => (
                        <td key={cell.id}>{cell.value as React.ReactNode}</td>
                      ))}
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </CarbonDataTable>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <ShadcnDataTable rows={ROWS} headers={HEADERS}>
          {({rows, headers, getHeaderProps, getRowProps}) => (
            <Table>
              <TableHeader>
                <TableRow>
                  {headers.map((header) => {
                    const props = getHeaderProps({header});
                    return (
                      <TableHead key={props.key as string}>
                        {header.header}
                      </TableHead>
                    );
                  })}
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((row) => {
                  const props = getRowProps({row});
                  return (
                    <TableRow key={props.key as string}>
                      {row.cells.map((cell) => (
                        <TableCell key={cell.id}>
                          {cell.value as React.ReactNode}
                        </TableCell>
                      ))}
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </ShadcnDataTable>
      </div>
    </div>
  ),
};

export const WithSort: Story = {
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (click headers to cycle NONE → ASC → DESC)
        </div>
        <ShadcnDataTable rows={ROWS} headers={HEADERS} isSortable>
          {({rows, headers, getHeaderProps, getRowProps}) => (
            <Table>
              <TableHeader>
                <TableRow>
                  {headers.map((header) => {
                    const props = getHeaderProps({header});
                    const Icon =
                      props.sortDirection === 'ASC'
                        ? ChevronUpIcon
                        : props.sortDirection === 'DESC'
                          ? ChevronDownIcon
                          : ChevronsUpDownIcon;
                    return (
                      <TableHead key={props.key as string}>
                        {props.isSortable ? (
                          <button
                            type="button"
                            onClick={props.onClick}
                            className="inline-flex items-center gap-1.5 font-medium"
                          >
                            {header.header}
                            <Icon className="size-3.5 text-muted-foreground" />
                          </button>
                        ) : (
                          header.header
                        )}
                      </TableHead>
                    );
                  })}
                </TableRow>
              </TableHeader>
              <TableBody>
                {rows.map((row) => {
                  const props = getRowProps({row});
                  return (
                    <TableRow key={props.key as string}>
                      {row.cells.map((cell) => (
                        <TableCell key={cell.id}>
                          {cell.value as React.ReactNode}
                        </TableCell>
                      ))}
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </ShadcnDataTable>
      </div>
    </div>
  ),
};

export const WithSelection: Story = {
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (header checkbox selects/deselects all; indeterminate when partial)
        </div>
        <ShadcnDataTable rows={ROWS} headers={HEADERS}>
          {({
            rows,
            headers,
            getHeaderProps,
            getRowProps,
            getSelectionProps,
            selectedRows,
          }) => {
            const selectAllProps = getSelectionProps();
            return (
              <>
                <div className="text-xs text-muted-foreground mb-2">
                  Selected: {selectedRows.length} / {rows.length}
                </div>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead className="w-10">
                        <Checkbox
                          checked={
                            selectAllProps.indeterminate
                              ? 'indeterminate'
                              : selectAllProps.checked
                          }
                          onCheckedChange={() =>
                            selectAllProps.onSelect(
                              {} as React.MouseEvent<HTMLInputElement>,
                            )
                          }
                          aria-label={selectAllProps['aria-label']}
                        />
                      </TableHead>
                      {headers.map((header) => {
                        const props = getHeaderProps({header});
                        return (
                          <TableHead key={props.key as string}>
                            {header.header}
                          </TableHead>
                        );
                      })}
                    </TableRow>
                  </TableHeader>
                  <TableBody>
                    {rows.map((row) => {
                      const rowProps = getRowProps({row});
                      const sel = getSelectionProps({row});
                      return (
                        <TableRow
                          key={rowProps.key as string}
                          data-state={sel.checked ? 'selected' : undefined}
                        >
                          <TableCell className="w-10">
                            <Checkbox
                              checked={sel.checked}
                              onCheckedChange={() =>
                                sel.onSelect(
                                  {} as React.MouseEvent<HTMLInputElement>,
                                )
                              }
                              aria-label={sel['aria-label']}
                            />
                          </TableCell>
                          {row.cells.map((cell) => (
                            <TableCell key={cell.id}>
                              {cell.value as React.ReactNode}
                            </TableCell>
                          ))}
                        </TableRow>
                      );
                    })}
                  </TableBody>
                </Table>
              </>
            );
          }}
        </ShadcnDataTable>
      </div>
    </div>
  ),
};
