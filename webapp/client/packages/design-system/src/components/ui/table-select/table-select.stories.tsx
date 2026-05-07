/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import * as React from 'react';
import {TableSelectAll, TableSelectRow} from './table-select.shadcn';
import {Table, TableBody, TableHead, TableHeader, TableRow, TableCell} from '../table/table.shadcn';

const ROWS = [
  {id: 'r1', name: 'Load Balancer 1'},
  {id: 'r2', name: 'Load Balancer 2'},
  {id: 'r3', name: 'Load Balancer 3'},
];

const meta: Meta = {title: 'UI/TableSelect'};
export default meta;
type Story = StoryObj;

export const Default: Story = {
  render: () => {
    const [selected, setSelected] = React.useState<Set<string>>(new Set());
    const allChecked = selected.size === ROWS.length;
    const indeterminate = selected.size > 0 && !allChecked;

    const toggleRow = (id: string) => {
      setSelected((prev) => {
        const next = new Set(prev);
        if (next.has(id)) next.delete(id);
        else next.add(id);
        return next;
      });
    };
    const toggleAll = () =>
      setSelected((prev) =>
        prev.size === ROWS.length ? new Set() : new Set(ROWS.map((r) => r.id)),
      );

    return (
      <div className="grid grid-cols-1 gap-12 pt-8">
        <div>
          <div className="text-sm font-semibold mb-4">
            shadcn checkbox selection ({selected.size} of {ROWS.length})
          </div>
          <Table>
            <TableHeader>
              <TableRow>
                <TableSelectAll
                  id="select-all"
                  name="select-all"
                  checked={allChecked}
                  indeterminate={indeterminate}
                  onSelect={toggleAll}
                  aria-label={allChecked ? 'Deselect all rows' : 'Select all rows'}
                />
                <TableHead>Name</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {ROWS.map((row) => (
                <TableRow
                  key={row.id}
                  data-state={selected.has(row.id) ? 'selected' : undefined}
                >
                  <TableSelectRow
                    id={`select-${row.id}`}
                    name={`select-${row.id}`}
                    checked={selected.has(row.id)}
                    onSelect={() => toggleRow(row.id)}
                    aria-label={
                      selected.has(row.id) ? 'Deselect row' : 'Select row'
                    }
                  />
                  <TableCell>{row.name}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </div>
    );
  },
};

export const RadioMode: Story = {
  render: () => {
    const [selected, setSelected] = React.useState<string | null>(null);
    return (
      <div className="grid grid-cols-1 gap-12 pt-8">
        <div>
          <div className="text-sm font-semibold mb-4">
            shadcn radio selection (single row at a time)
          </div>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="w-10" />
                <TableHead>Name</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {ROWS.map((row) => (
                <TableRow
                  key={row.id}
                  data-state={selected === row.id ? 'selected' : undefined}
                >
                  <TableSelectRow
                    radio
                    id={`select-${row.id}`}
                    name="row-select"
                    checked={selected === row.id}
                    onSelect={() => setSelected(row.id)}
                    aria-label="Select row"
                  />
                  <TableCell>{row.name}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      </div>
    );
  },
};
