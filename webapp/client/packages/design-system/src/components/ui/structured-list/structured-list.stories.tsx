/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {
  StructuredListBody as AdapterStructuredListBody,
  StructuredListCell as AdapterStructuredListCell,
  StructuredListHead as AdapterStructuredListHead,
  StructuredListRow as AdapterStructuredListRow,
  StructuredListWrapper as AdapterStructuredListWrapper,
} from './structured-list.adapter';
import {
  StructuredListBody as CarbonStructuredListBody,
  StructuredListCell as CarbonStructuredListCell,
  StructuredListHead as CarbonStructuredListHead,
  StructuredListRow as CarbonStructuredListRow,
  StructuredListWrapper as CarbonStructuredListWrapper,
} from './structured-list.carbon';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from './structured-list.shadcn';

const ROWS = [
  {
    name: 'Stem cells',
    description: 'Tissue regeneration',
    category: 'Biology',
  },
  {
    name: 'CRISPR',
    description: 'Targeted genome editing',
    category: 'Biology',
  },
  {
    name: 'Quantum computing',
    description: 'Computation via qubits',
    category: 'Computing',
  },
];

const meta: Meta = {
  title: 'UI/StructuredList',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonStructuredListWrapper>
          <CarbonStructuredListHead>
            <CarbonStructuredListRow head>
              <CarbonStructuredListCell head>
                ColumnA
              </CarbonStructuredListCell>
              <CarbonStructuredListCell head>
                ColumnB
              </CarbonStructuredListCell>
              <CarbonStructuredListCell head>
                ColumnC
              </CarbonStructuredListCell>
            </CarbonStructuredListRow>
          </CarbonStructuredListHead>
          <CarbonStructuredListBody>
            {ROWS.map((row) => (
              <CarbonStructuredListRow key={row.name}>
                <CarbonStructuredListCell>{row.name}</CarbonStructuredListCell>
                <CarbonStructuredListCell>
                  {row.description}
                </CarbonStructuredListCell>
                <CarbonStructuredListCell>
                  {row.category}
                </CarbonStructuredListCell>
              </CarbonStructuredListRow>
            ))}
          </CarbonStructuredListBody>
        </CarbonStructuredListWrapper>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (Table)</div>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>ColumnA</TableHead>
              <TableHead>ColumnB</TableHead>
              <TableHead>ColumnC</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {ROWS.map((row) => (
              <TableRow key={row.name}>
                <TableCell>{row.name}</TableCell>
                <TableCell>{row.description}</TableCell>
                <TableCell>{row.category}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterStructuredListWrapper>
          <AdapterStructuredListHead>
            <AdapterStructuredListRow head>
              <AdapterStructuredListCell head>
                ColumnA
              </AdapterStructuredListCell>
              <AdapterStructuredListCell head>
                ColumnB
              </AdapterStructuredListCell>
              <AdapterStructuredListCell head>
                ColumnC
              </AdapterStructuredListCell>
            </AdapterStructuredListRow>
          </AdapterStructuredListHead>
          <AdapterStructuredListBody>
            {ROWS.map((row) => (
              <AdapterStructuredListRow key={row.name}>
                <AdapterStructuredListCell>{row.name}</AdapterStructuredListCell>
                <AdapterStructuredListCell>
                  {row.description}
                </AdapterStructuredListCell>
                <AdapterStructuredListCell>
                  {row.category}
                </AdapterStructuredListCell>
              </AdapterStructuredListRow>
            ))}
          </AdapterStructuredListBody>
        </AdapterStructuredListWrapper>
      </div>
    </div>
  ),
};

export const KeyValue: Story = {
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          Carbon (typical key/value list, no head row)
        </div>
        <CarbonStructuredListWrapper>
          <CarbonStructuredListBody>
            <CarbonStructuredListRow>
              <CarbonStructuredListCell noWrap>Email</CarbonStructuredListCell>
              <CarbonStructuredListCell>
                user@example.com
              </CarbonStructuredListCell>
            </CarbonStructuredListRow>
            <CarbonStructuredListRow>
              <CarbonStructuredListCell noWrap>Role</CarbonStructuredListCell>
              <CarbonStructuredListCell>Admin</CarbonStructuredListCell>
            </CarbonStructuredListRow>
            <CarbonStructuredListRow>
              <CarbonStructuredListCell noWrap>
                Joined
              </CarbonStructuredListCell>
              <CarbonStructuredListCell>
                Jan 12, 2024
              </CarbonStructuredListCell>
            </CarbonStructuredListRow>
          </CarbonStructuredListBody>
        </CarbonStructuredListWrapper>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (Table without TableHeader)
        </div>
        <Table>
          <TableBody>
            <TableRow>
              <TableCell className="font-medium whitespace-nowrap w-[120px]">
                Email
              </TableCell>
              <TableCell>user@example.com</TableCell>
            </TableRow>
            <TableRow>
              <TableCell className="font-medium whitespace-nowrap w-[120px]">
                Role
              </TableCell>
              <TableCell>Admin</TableCell>
            </TableRow>
            <TableRow>
              <TableCell className="font-medium whitespace-nowrap w-[120px]">
                Joined
              </TableCell>
              <TableCell>Jan 12, 2024</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterStructuredListWrapper>
          <AdapterStructuredListBody>
            <AdapterStructuredListRow>
              <AdapterStructuredListCell noWrap>Email</AdapterStructuredListCell>
              <AdapterStructuredListCell>
                user@example.com
              </AdapterStructuredListCell>
            </AdapterStructuredListRow>
            <AdapterStructuredListRow>
              <AdapterStructuredListCell noWrap>Role</AdapterStructuredListCell>
              <AdapterStructuredListCell>Admin</AdapterStructuredListCell>
            </AdapterStructuredListRow>
            <AdapterStructuredListRow>
              <AdapterStructuredListCell noWrap>
                Joined
              </AdapterStructuredListCell>
              <AdapterStructuredListCell>
                Jan 12, 2024
              </AdapterStructuredListCell>
            </AdapterStructuredListRow>
          </AdapterStructuredListBody>
        </AdapterStructuredListWrapper>
      </div>
    </div>
  ),
};
