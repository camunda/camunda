/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {TableContainer as CarbonTableContainer} from './table-container.carbon';
import {TableContainer as AdapterTableContainer} from './table-container.adapter';
import {TableContainer} from './table-container.shadcn';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../table/table.shadcn';

const ROWS = [
  {invoice: 'INV001', amount: '$250.00'},
  {invoice: 'INV002', amount: '$150.00'},
];

const InnerTable = () => (
  <Table>
    <TableHeader>
      <TableRow>
        <TableHead>Invoice</TableHead>
        <TableHead className="text-right">Amount</TableHead>
      </TableRow>
    </TableHeader>
    <TableBody>
      {ROWS.map((row) => (
        <TableRow key={row.invoice}>
          <TableCell>{row.invoice}</TableCell>
          <TableCell className="text-right">{row.amount}</TableCell>
        </TableRow>
      ))}
    </TableBody>
  </Table>
);

const meta: Meta = {title: 'UI/TableContainer'};
export default meta;
type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonTableContainer
          title="Recent invoices"
          description="A summary of the last two invoices."
        >
          <InnerTable />
        </CarbonTableContainer>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <TableContainer
          title="Recent invoices"
          description="A summary of the last two invoices."
        >
          <InnerTable />
        </TableContainer>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterTableContainer
          title="Recent invoices"
          description="A summary of the last two invoices."
        >
          <InnerTable />
        </AdapterTableContainer>
      </div>
    </div>
  ),
};

export const StaticWidth: Story = {
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (`fitContent` shrinks to inner table width)
        </div>
        <TableContainer fitContent title="Compact">
          <InnerTable />
        </TableContainer>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          Adapter (Carbon `useStaticWidth` → `fitContent`)
        </div>
        <AdapterTableContainer useStaticWidth title="Compact">
          <InnerTable />
        </AdapterTableContainer>
      </div>
    </div>
  ),
};
