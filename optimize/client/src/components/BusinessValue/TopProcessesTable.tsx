/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  DataTable,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@carbon/react';

import {t} from 'translation';

import type {TopProcess} from './types';

import './TopProcessesTable.scss';

interface TopProcessesTableProps {
  processes: TopProcess[];
}

const formatCurrency = (value: number) => `€${value.toLocaleString()}`;
const formatNumber = (value: number) => value.toLocaleString();

const formatters: Record<string, (value: number) => string> = {
  valueCreated: formatCurrency,
  baselineCostSaved: formatCurrency,
  llmCost: formatCurrency,
  instanceCount: formatNumber,
};

function buildHeaders() {
  return [
    {key: 'processLabel', header: t('businessValue.table.process').toString()},
    {key: 'valueCreated', header: t('businessValue.table.valueCreated').toString()},
    {key: 'baselineCostSaved', header: t('businessValue.table.costSaved').toString()},
    {key: 'llmCost', header: t('businessValue.table.llmCost').toString()},
    {key: 'instanceCount', header: t('businessValue.table.instances').toString()},
  ];
}

export default function TopProcessesTable({processes}: TopProcessesTableProps) {
  const headers = buildHeaders();

  const rows = processes.map((process) => ({
    id: process.processDefinitionKey,
    processLabel: process.processLabel,
    valueCreated: process.valueCreated,
    baselineCostSaved: process.baselineCostSaved,
    llmCost: process.llmCost,
    instanceCount: process.instanceCount,
  }));

  return (
    <div className="TopProcessesTable">
      <DataTable rows={rows} headers={headers} size="md" isSortable>
        {({rows, headers, getTableProps, getHeaderProps, getRowProps}) => (
          <Table {...getTableProps()}>
            <TableHead>
              <TableRow>
                {headers.map((header) => (
                  <TableHeader {...getHeaderProps({header, isSortable: true})} key={header.key}>
                    {header.header}
                  </TableHeader>
                ))}
              </TableRow>
            </TableHead>
            <TableBody>
              {rows.map((row) => (
                <TableRow {...getRowProps({row})} key={row.id}>
                  {row.cells.map((cell) => {
                    const formatter = formatters[cell.info?.header as string];
                    return (
                      <TableCell key={cell.id}>
                        {formatter ? formatter(cell.value) : cell.value}
                      </TableCell>
                    );
                  })}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </DataTable>
    </div>
  );
}
