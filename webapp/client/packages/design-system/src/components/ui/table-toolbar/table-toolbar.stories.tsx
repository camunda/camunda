/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {
  TableToolbar as CarbonTableToolbar,
  TableToolbarContent as CarbonTableToolbarContent,
} from './table-toolbar.carbon';
import {
  TableToolbar as AdapterTableToolbar,
  TableToolbarContent as AdapterTableToolbarContent,
} from './table-toolbar.adapter';
import {
  TableToolbar,
  TableToolbarContent,
} from './table-toolbar.shadcn';
import {Button} from '../button/button.shadcn';

const meta: Meta = {title: 'UI/TableToolbar'};
export default meta;
type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonTableToolbar aria-label="data table toolbar">
          <CarbonTableToolbarContent>
            <button type="button" className="cds--btn cds--btn--ghost">
              Action
            </button>
            <button type="button" className="cds--btn cds--btn--primary">
              Add
            </button>
          </CarbonTableToolbarContent>
        </CarbonTableToolbar>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <TableToolbar>
          <TableToolbarContent>
            <Button variant="ghost" size="sm">
              Action
            </Button>
            <Button size="sm">Add</Button>
          </TableToolbarContent>
        </TableToolbar>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterTableToolbar aria-label="data table toolbar">
          <AdapterTableToolbarContent>
            <Button variant="ghost" size="sm">
              Action
            </Button>
            <Button size="sm">Add</Button>
          </AdapterTableToolbarContent>
        </AdapterTableToolbar>
      </div>
    </div>
  ),
};
