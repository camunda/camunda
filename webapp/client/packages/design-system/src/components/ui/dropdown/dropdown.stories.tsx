/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {Dropdown as CarbonDropdown} from './dropdown.carbon';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from './dropdown.shadcn';

type Framework = {id: string; label: string};

const FRAMEWORKS: Framework[] = [
  {id: 'next', label: 'Next.js'},
  {id: 'remix', label: 'Remix'},
  {id: 'astro', label: 'Astro'},
  {id: 'sveltekit', label: 'SvelteKit'},
];

const meta: Meta = {
  title: 'UI/Dropdown',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonDropdown
          id="dd-carbon-default"
          titleText="Framework"
          label="Select framework"
          items={FRAMEWORKS}
          itemToString={(item) => (item ? item.label : '')}
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (Select)</div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">Framework</label>
          <Select>
            <SelectTrigger>
              <SelectValue placeholder="Select framework" />
            </SelectTrigger>
            <SelectContent>
              {FRAMEWORKS.map((item) => (
                <SelectItem key={item.id} value={item.id}>
                  {item.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>
    </div>
  ),
};

export const Disabled: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonDropdown
          id="dd-carbon-disabled"
          titleText="Framework"
          label="Select framework"
          items={FRAMEWORKS}
          itemToString={(item) => (item ? item.label : '')}
          initialSelectedItem={FRAMEWORKS[0]}
          disabled
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">Framework</label>
          <Select disabled defaultValue="next">
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {FRAMEWORKS.map((item) => (
                <SelectItem key={item.id} value={item.id}>
                  {item.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </div>
    </div>
  ),
};

export const Invalid: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonDropdown
          id="dd-carbon-invalid"
          titleText="Framework"
          label="Select framework"
          items={FRAMEWORKS}
          itemToString={(item) => (item ? item.label : '')}
          invalid
          invalidText="Pick a framework to continue"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">Framework</label>
          <Select>
            <SelectTrigger
              aria-invalid
              className="border-destructive focus:ring-destructive"
            >
              <SelectValue placeholder="Select framework" />
            </SelectTrigger>
            <SelectContent>
              {FRAMEWORKS.map((item) => (
                <SelectItem key={item.id} value={item.id}>
                  {item.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
          <p className="text-xs text-destructive">
            Pick a framework to continue
          </p>
        </div>
      </div>
    </div>
  ),
};
