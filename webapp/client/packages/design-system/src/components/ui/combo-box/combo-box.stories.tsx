/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {ComboBox as AdapterComboBox} from './combo-box.adapter';
import {ComboBox as CarbonComboBox} from './combo-box.carbon';
import {
  Combobox,
  ComboboxContent,
  ComboboxEmpty,
  ComboboxInput,
  ComboboxItem,
  ComboboxList,
  ComboboxValue,
} from './combo-box.shadcn';

type Framework = {id: string; label: string};

const FRAMEWORKS: Framework[] = [
  {id: 'next', label: 'Next.js'},
  {id: 'remix', label: 'Remix'},
  {id: 'astro', label: 'Astro'},
  {id: 'sveltekit', label: 'SvelteKit'},
  {id: 'nuxt', label: 'Nuxt.js'},
];

const meta: Meta = {
  title: 'UI/ComboBox',
};
export default meta;

type Story = StoryObj;

const ShadcnComboBox = ({
  defaultValue,
  showClear,
  disabled,
}: {
  defaultValue?: Framework;
  showClear?: boolean;
  disabled?: boolean;
}) => (
  <Combobox
    items={FRAMEWORKS}
    itemToStringLabel={(i: Framework) => i.label}
    defaultValue={defaultValue}
  >
    <ComboboxValue>
      <ComboboxInput
        placeholder="Select framework"
        disabled={disabled}
        showClear={showClear}
      />
    </ComboboxValue>
    <ComboboxContent>
      <ComboboxEmpty>No framework found.</ComboboxEmpty>
      <ComboboxList>
        {FRAMEWORKS.map((item) => (
          <ComboboxItem key={item.id} value={item}>
            {item.label}
          </ComboboxItem>
        ))}
      </ComboboxList>
    </ComboboxContent>
  </Combobox>
);

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonComboBox
          id="cb-carbon-default"
          items={FRAMEWORKS}
          itemToString={(item) => (item ? item.label : '')}
          titleText="Framework"
          placeholder="Select framework"
          onChange={() => {}}
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">Framework</label>
          <ShadcnComboBox />
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterComboBox
          id="cb-adapter-default"
          items={FRAMEWORKS}
          itemToString={(item) => (item ? item.label : '')}
          titleText="Framework"
          placeholder="Select framework"
          onChange={() => {}}
        />
      </div>
    </div>
  ),
};

export const Disabled: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonComboBox
          id="cb-carbon-disabled"
          items={FRAMEWORKS}
          itemToString={(item) => (item ? item.label : '')}
          titleText="Framework"
          placeholder="Select framework"
          disabled
          onChange={() => {}}
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex flex-col gap-1.5 opacity-50">
          <label className="text-sm font-medium">Framework</label>
          <ShadcnComboBox disabled />
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterComboBox
          id="cb-adapter-disabled"
          items={FRAMEWORKS}
          itemToString={(item) => (item ? item.label : '')}
          titleText="Framework"
          placeholder="Select framework"
          disabled
          onChange={() => {}}
        />
      </div>
    </div>
  ),
};

export const WithClear: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonComboBox
          id="cb-carbon-clear"
          items={FRAMEWORKS}
          itemToString={(item) => (item ? item.label : '')}
          titleText="Framework"
          placeholder="Select framework"
          selectedItem={FRAMEWORKS[0]}
          onChange={() => {}}
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <div className="flex flex-col gap-1.5">
          <label className="text-sm font-medium">Framework</label>
          <ShadcnComboBox defaultValue={FRAMEWORKS[0]} showClear />
        </div>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterComboBox
          id="cb-adapter-clear"
          items={FRAMEWORKS}
          itemToString={(item) => (item ? item.label : '')}
          titleText="Framework"
          placeholder="Select framework"
          selectedItem={FRAMEWORKS[0]}
          onChange={() => {}}
        />
      </div>
    </div>
  ),
};
