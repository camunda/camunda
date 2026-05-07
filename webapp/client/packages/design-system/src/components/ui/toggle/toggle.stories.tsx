/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {Meta, StoryObj} from '@storybook/react';
import {Toggle as AdapterToggle} from './toggle.adapter';
import {Toggle as CarbonToggle} from './toggle.carbon';
import {Switch} from './toggle.shadcn';

const meta: Meta = {
  title: 'UI/Toggle',
};
export default meta;

type Story = StoryObj;

export const Default: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonToggle id="toggle-carbon-default" labelText="Notifications" />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (Switch)</div>
        <label className="flex items-center gap-2 text-sm">
          <Switch id="toggle-shadcn-default" />
          <span>Notifications</span>
        </label>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterToggle id="toggle-adapter-default" labelText="Notifications" />
      </div>
    </div>
  ),
};

export const Checked: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon</div>
        <CarbonToggle
          id="toggle-carbon-checked"
          labelText="Auto-save"
          defaultToggled
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <label className="flex items-center gap-2 text-sm">
          <Switch id="toggle-shadcn-checked" defaultChecked />
          <span>Auto-save</span>
        </label>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterToggle
          id="toggle-adapter-checked"
          labelText="Auto-save"
          defaultToggled
        />
      </div>
    </div>
  ),
};

export const SmallSize: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">Carbon (`size="sm"`)</div>
        <CarbonToggle
          id="toggle-carbon-sm"
          size="sm"
          labelText="Compact view"
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn (`size="sm"`)</div>
        <label className="flex items-center gap-2 text-sm">
          <Switch id="toggle-shadcn-sm" size="sm" />
          <span>Compact view</span>
        </label>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          Adapter (Carbon API, size="sm")
        </div>
        <AdapterToggle
          id="toggle-adapter-sm"
          size="sm"
          labelText="Compact view"
        />
      </div>
    </div>
  ),
};

export const WithOnOffLabels: Story = {
  render: () => (
    <div className="grid grid-cols-3 gap-12 pt-8">
      <div>
        <div className="text-sm font-semibold mb-4">
          Carbon (built-in `On`/`Off` text labels)
        </div>
        <CarbonToggle
          id="toggle-carbon-onoff"
          labelText="Beta features"
          labelA="Off"
          labelB="On"
          defaultToggled
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">
          shadcn (no built-in labels — render manually)
        </div>
        <label className="flex items-center gap-2 text-sm">
          <span className="text-muted-foreground">Off</span>
          <Switch id="toggle-shadcn-onoff" defaultChecked />
          <span className="text-muted-foreground">On</span>
          <span className="ml-3">Beta features</span>
        </label>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterToggle
          id="toggle-adapter-onoff"
          labelText="Beta features"
          labelA="Off"
          labelB="On"
          defaultToggled
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
        <CarbonToggle
          id="toggle-carbon-disabled"
          labelText="Locked option"
          disabled
        />
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">shadcn</div>
        <label className="flex items-center gap-2 text-sm opacity-50">
          <Switch id="toggle-shadcn-disabled" disabled />
          <span>Locked option</span>
        </label>
      </div>
      <div>
        <div className="text-sm font-semibold mb-4">Adapter (Carbon API)</div>
        <AdapterToggle
          id="toggle-adapter-disabled"
          labelText="Locked option"
          disabled
        />
      </div>
    </div>
  ),
};
