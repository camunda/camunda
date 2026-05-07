/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {useState} from 'react';
import {Checkbox as AdapterCheckbox} from './checkbox.adapter';
import {Checkbox as CarbonCheckbox} from './checkbox.carbon';
import {Checkbox as ShadcnCheckbox} from './checkbox.shadcn';

const meta: Meta = {
  title: 'UI/Checkbox',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonCheckbox id="cb-default-carbon" labelText="Accept terms" />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <label className="flex items-center gap-2 text-sm">
          <ShadcnCheckbox id="cb-default-shadcn" />
          <span>Accept terms</span>
        </label>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterCheckbox id="cb-default-adapter" labelText="Accept terms" />
      </div>
    </div>
  ),
};

export const Checked: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonCheckbox
          id="cb-checked-carbon"
          labelText="Notifications"
          defaultChecked
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <label className="flex items-center gap-2 text-sm">
          <ShadcnCheckbox id="cb-checked-shadcn" defaultChecked />
          <span>Notifications</span>
        </label>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterCheckbox
          id="cb-checked-adapter"
          labelText="Notifications"
          defaultChecked
        />
      </div>
    </div>
  ),
};

export const Indeterminate: Story = {
  render: () => {
    const Carbon = () => (
      <CarbonCheckbox
        id="cb-indeterminate-carbon"
        labelText="Select all"
        indeterminate
      />
    );
    const Shadcn = () => {
      const [checked, setChecked] = useState<boolean | 'indeterminate'>(
        'indeterminate',
      );
      return (
        <label className="flex items-center gap-2 text-sm">
          <ShadcnCheckbox
            id="cb-indeterminate-shadcn"
            checked={checked}
            onCheckedChange={setChecked}
          />
          <span>Select all</span>
        </label>
      );
    };
    return (
      <div className="grid grid-cols-3 gap-12 pt-8">
        <div>
          <div className="text-sm font-semibold mb-4">Carbon</div>
          <Carbon />
        </div>
        <div>
          <div className="text-sm font-semibold mb-4">shadcn</div>
          <Shadcn />
        </div>
        <div>
          <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
          <AdapterCheckbox
            id="cb-indeterminate-adapter"
            labelText="Select all"
            indeterminate
          />
        </div>
      </div>
    );
  },
};

export const Disabled: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonCheckbox id="cb-disabled-carbon" labelText="Disabled" disabled />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <label className="flex items-center gap-2 text-sm opacity-50">
          <ShadcnCheckbox id="cb-disabled-shadcn" disabled />
          <span>Disabled</span>
        </label>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterCheckbox
          id="cb-disabled-adapter"
          labelText="Disabled"
          disabled
        />
      </div>
    </div>
  ),
};

export const Invalid: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonCheckbox
          id="cb-invalid-carbon"
          labelText="Required"
          invalid
          invalidText="This field is required"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex flex-col gap-1">
          <label className="flex items-center gap-2 text-sm">
            <ShadcnCheckbox
              id="cb-invalid-shadcn"
              aria-invalid
              className="border-destructive data-[state=checked]:bg-destructive"
            />
            <span>Required</span>
          </label>
          <span className="text-xs text-destructive">
            This field is required
          </span>
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterCheckbox
          id="cb-invalid-adapter"
          labelText="Required"
          invalid
          invalidText="This field is required"
        />
      </div>
    </div>
  ),
};
