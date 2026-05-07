/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {DataTableSkeleton as CarbonDataTableSkeleton} from './data-table-skeleton.carbon';
import {DataTableSkeleton as AdapterDataTableSkeleton} from './data-table-skeleton.adapter';
import {DataTableSkeleton} from './data-table-skeleton.shadcn';

const meta: Meta = {title: 'UI/DataTableSkeleton'};
export default meta;
type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-1 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonDataTableSkeleton columnCount={4} rowCount={5} />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <DataTableSkeleton columnCount={4} rowCount={5} />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterDataTableSkeleton columnCount={4} rowCount={5} />
      </div>
    </div>
  ),
};

export const WithCustomHeaders: Story = {
  render: () => (
    <DataTableSkeleton
      headers={[
        {key: 'name', header: 'Name'},
        {key: 'protocol', header: 'Protocol'},
        {key: 'port', header: 'Port'},
        {key: 'rule', header: 'Rule'},
      ]}
      rowCount={3}
    />
  ),
};

export const NoToolbarOrHeader: Story = {
  render: () => (
    <DataTableSkeleton
      columnCount={3}
      rowCount={4}
      showToolbar={false}
      showHeader={false}
    />
  ),
};
