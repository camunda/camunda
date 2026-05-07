/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import * as React from 'react';
import {
  TableExpandHeader,
  TableExpandRow,
  TableExpandedRow,
} from './table-expand.shadcn';
import {Table, TableBody, TableHead, TableHeader, TableRow, TableCell} from '../table/table.shadcn';

const ROWS = [
  {id: '1', name: 'Load Balancer 1', status: 'Disabled'},
  {id: '2', name: 'Load Balancer 2', status: 'Starting'},
  {id: '3', name: 'Load Balancer 3', status: 'Active'},
];

const meta: Meta = {title: 'UI/TableExpand'};
export default meta;
type Story = StoryObj;

export const Default: Story = {
  render: () => {
    const [expanded, setExpanded] = React.useState<Record<string, boolean>>({});
    const toggle = (id: string) =>
      setExpanded((e) => ({...e, [id]: !e[id]}));

    return (
      <div className="grid grid-cols-1 gap-12 pt-8">
        <div>
          <div className="text-sm font-semibold mb-4">
            shadcn (click chevron to expand)
          </div>
          <Table>
            <TableHeader>
              <TableRow>
                <TableExpandHeader />
                <TableHead>Name</TableHead>
                <TableHead>Status</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {ROWS.map((row) => (
                <React.Fragment key={row.id}>
                  <TableExpandRow
                    aria-label={
                      expanded[row.id] ? 'Collapse row' : 'Expand row'
                    }
                    isExpanded={expanded[row.id]}
                    onExpand={() => toggle(row.id)}
                  >
                    <TableCell>{row.name}</TableCell>
                    <TableCell>{row.status}</TableCell>
                  </TableExpandRow>
                  {expanded[row.id] && (
                    <TableExpandedRow colSpan={3}>
                      <div className="text-sm">
                        Details for <strong>{row.name}</strong>: lorem ipsum
                        dolor sit amet, consectetur adipiscing elit.
                      </div>
                    </TableExpandedRow>
                  )}
                </React.Fragment>
              ))}
            </TableBody>
          </Table>
        </div>
      </div>
    );
  },
};
