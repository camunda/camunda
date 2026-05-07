/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {MultiSelect as CarbonMultiSelect} from '@carbon/react';
import * as React from 'react';
import {MultiSelect} from './multi-select.shadcn';

const meta: Meta = {
  title: 'UI/MultiSelect',
};
export default meta;

type Story = StoryObj;

type Item = {id: string; label: string};

const items: Item[] = [
  {id: 'all', label: 'All states'},
  {id: 'active', label: 'Active'},
  {id: 'completed', label: 'Completed'},
  {id: 'incidents', label: 'Incidents'},
  {id: 'cancelled', label: 'Cancelled'},
];

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonMultiSelect
          id="carbon-default"
          items={items}
          itemToString={(i) => i?.label ?? ''}
          label="Select states"
          titleText="State filter"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <MultiSelect
          id="shadcn-default"
          items={items}
          itemToString={(i) => i.label}
          label="Select states"
          titleText="State filter"
        />
      </div>
    </div>
  ),
};

export const Controlled: Story = {
  render: () => {
    const [carbonSel, setCarbonSel] = React.useState<Item[]>([items[1]!]);
    const [shadcnSel, setShadcnSel] = React.useState<Item[]>([items[1]!]);
    return (
      <div className="grid grid-cols-2 gap-12 pt-8">
        <div>
          <div className="text-sm font-semibold mb-4">Carbon (controlled)</div>
          <CarbonMultiSelect
            id="carbon-controlled"
            items={items}
            itemToString={(i) => i?.label ?? ''}
            label="States"
            titleText="State filter"
            selectedItems={carbonSel}
            onChange={({selectedItems}) =>
              setCarbonSel((selectedItems ?? []) as Item[])
            }
          />
          <p className="mt-2 text-xs text-muted-foreground">
            Selected: {carbonSel.map((i) => i.label).join(', ') || '(none)'}
          </p>
        </div>
        <div>
          <div className="text-sm font-semibold mb-4">shadcn (controlled)</div>
          <MultiSelect
            id="shadcn-controlled"
            items={items}
            itemToString={(i) => i.label}
            label="States"
            titleText="State filter"
            selectedItems={shadcnSel}
            onChange={({selectedItems}) => setShadcnSel(selectedItems)}
          />
          <p className="mt-2 text-xs text-muted-foreground">
            Selected: {shadcnSel.map((i) => i.label).join(', ') || '(none)'}
          </p>
        </div>
      </div>
    );
  },
};

export const Invalid: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (invalid)</div>
        <CarbonMultiSelect
          id="carbon-invalid"
          items={items}
          itemToString={(i) => i?.label ?? ''}
          label="States"
          titleText="State filter"
          invalid
          invalidText="At least one state is required"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (invalid)</div>
        <MultiSelect
          id="shadcn-invalid"
          items={items}
          itemToString={(i) => i.label}
          label="States"
          titleText="State filter"
          invalid
          invalidText="At least one state is required"
        />
      </div>
    </div>
  ),
};

export const Disabled: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (disabled)</div>
        <CarbonMultiSelect
          id="carbon-disabled"
          items={items}
          itemToString={(i) => i?.label ?? ''}
          label="States"
          titleText="State filter"
          disabled
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (disabled)</div>
        <MultiSelect
          id="shadcn-disabled"
          items={items}
          itemToString={(i) => i.label}
          label="States"
          titleText="State filter"
          disabled
        />
      </div>
    </div>
  ),
};
