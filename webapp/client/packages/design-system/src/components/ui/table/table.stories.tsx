/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {
  Table as CarbonTable,
  TableBody as CarbonTableBody,
  TableCell as CarbonTableCell,
  TableHead as CarbonTableHead,
  TableHeader as CarbonTableHeader,
  TableRow as CarbonTableRow,
} from './table.carbon';
import {
  Table,
  TableBody,
  TableCaption,
  TableCell,
  TableFooter,
  TableHead,
  TableHeader,
  TableRow,
} from './table.shadcn';

const ROWS = [
  {invoice: 'INV001', status: 'Paid', method: 'Credit Card', amount: '$250.00'},
  {invoice: 'INV002', status: 'Pending', method: 'PayPal', amount: '$150.00'},
  {invoice: 'INV003', status: 'Unpaid', method: 'Bank Transfer', amount: '$350.00'},
  {invoice: 'INV004', status: 'Paid', method: 'Credit Card', amount: '$450.00'},
];

const meta: Meta = {
  title: 'UI/Table',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        {/* Carbon: TableHead = <thead>, TableHeader = <th>, TableBody = <tbody>, TableCell = <td> */}
        <CarbonTable>
          <CarbonTableHead>
            <CarbonTableRow>
              <CarbonTableHeader>Invoice</CarbonTableHeader>
              <CarbonTableHeader>Status</CarbonTableHeader>
              <CarbonTableHeader>Method</CarbonTableHeader>
              <CarbonTableHeader>Amount</CarbonTableHeader>
            </CarbonTableRow>
          </CarbonTableHead>
          <CarbonTableBody>
            {ROWS.map((row) => (
              <CarbonTableRow key={row.invoice}>
                <CarbonTableCell>{row.invoice}</CarbonTableCell>
                <CarbonTableCell>{row.status}</CarbonTableCell>
                <CarbonTableCell>{row.method}</CarbonTableCell>
                <CarbonTableCell>{row.amount}</CarbonTableCell>
              </CarbonTableRow>
            ))}
          </CarbonTableBody>
        </CarbonTable>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        {/* shadcn: TableHeader = <thead>, TableHead = <th>, TableBody = <tbody>, TableCell = <td> */}
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Invoice</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Method</TableHead>
              <TableHead className="text-right">Amount</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {ROWS.map((row) => (
              <TableRow key={row.invoice}>
                <TableCell className="font-medium">{row.invoice}</TableCell>
                <TableCell>{row.status}</TableCell>
                <TableCell>{row.method}</TableCell>
                <TableCell className="text-right">{row.amount}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  ),
};

export const WithFooterAndCaption: Story = {
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          Carbon (no footer / caption primitives in the wrapper — render plain
          tfoot / caption manually)
        </div>
        <CarbonTable>
          <CarbonTableHead>
            <CarbonTableRow>
              <CarbonTableHeader>Invoice</CarbonTableHeader>
              <CarbonTableHeader>Amount</CarbonTableHeader>
            </CarbonTableRow>
          </CarbonTableHead>
          <CarbonTableBody>
            {ROWS.map((row) => (
              <CarbonTableRow key={row.invoice}>
                <CarbonTableCell>{row.invoice}</CarbonTableCell>
                <CarbonTableCell>{row.amount}</CarbonTableCell>
              </CarbonTableRow>
            ))}
          </CarbonTableBody>
        </CarbonTable>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <Table>
          <TableCaption>A list of recent invoices.</TableCaption>
          <TableHeader>
            <TableRow>
              <TableHead>Invoice</TableHead>
              <TableHead className="text-right">Amount</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {ROWS.map((row) => (
              <TableRow key={row.invoice}>
                <TableCell className="font-medium">{row.invoice}</TableCell>
                <TableCell className="text-right">{row.amount}</TableCell>
              </TableRow>
            ))}
          </TableBody>
          <TableFooter>
            <TableRow>
              <TableCell>Total</TableCell>
              <TableCell className="text-right">$1,200.00</TableCell>
            </TableRow>
          </TableFooter>
        </Table>
      </div>
    </div>
  ),
};
