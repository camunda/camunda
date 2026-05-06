/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {
  Select as CarbonSelect,
  SelectItem as CarbonSelectItem,
} from './select.carbon';
import {
  Select,
  SelectContent,
  SelectGroup,
  SelectItem,
  SelectLabel,
  SelectSeparator,
  SelectTrigger,
  SelectValue,
} from './select.shadcn';

const meta: Meta = {
  title: 'UI/Select',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonSelect
          id="select-carbon-default"
          labelText="Framework"
          defaultValue="placeholder-item"
        >
          <CarbonSelectItem
            disabled
            hidden
            value="placeholder-item"
            text="Select framework"
          />
          <CarbonSelectItem value="next" text="Next.js" />
          <CarbonSelectItem value="remix" text="Remix" />
          <CarbonSelectItem value="astro" text="Astro" />
          <CarbonSelectItem value="sveltekit" text="SvelteKit" />
        </CarbonSelect>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">Framework</label>
          <Select>
            <SelectTrigger>
              <SelectValue placeholder="Select framework" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="next">Next.js</SelectItem>
              <SelectItem value="remix">Remix</SelectItem>
              <SelectItem value="astro">Astro</SelectItem>
              <SelectItem value="sveltekit">SvelteKit</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>
    </div>
  ),
};

export const Grouped: Story = {
  render: () => (
    <div className="grid grid-cols-2 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonSelect
          id="select-carbon-grouped"
          labelText="Timezone"
          defaultValue="UTC"
        >
          <CarbonSelectItem value="UTC" text="UTC" />
          <CarbonSelectItem value="CET" text="Europe / CET" />
          <CarbonSelectItem value="EST" text="America / EST" />
          <CarbonSelectItem value="JST" text="Asia / JST" />
        </CarbonSelect>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">Timezone</label>
          <Select defaultValue="UTC">
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectGroup>
                <SelectLabel>Universal</SelectLabel>
                <SelectItem value="UTC">UTC</SelectItem>
              </SelectGroup>
              <SelectSeparator />
              <SelectGroup>
                <SelectLabel>Europe</SelectLabel>
                <SelectItem value="CET">Europe / CET</SelectItem>
              </SelectGroup>
              <SelectSeparator />
              <SelectGroup>
                <SelectLabel>Americas</SelectLabel>
                <SelectItem value="EST">America / EST</SelectItem>
              </SelectGroup>
              <SelectSeparator />
              <SelectGroup>
                <SelectLabel>Asia</SelectLabel>
                <SelectItem value="JST">Asia / JST</SelectItem>
              </SelectGroup>
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
        <CarbonSelect
          id="select-carbon-disabled"
          labelText="Framework"
          disabled
          defaultValue="next"
        >
          <CarbonSelectItem value="next" text="Next.js" />
          <CarbonSelectItem value="remix" text="Remix" />
        </CarbonSelect>
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
              <SelectItem value="next">Next.js</SelectItem>
              <SelectItem value="remix">Remix</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>
    </div>
  ),
};
